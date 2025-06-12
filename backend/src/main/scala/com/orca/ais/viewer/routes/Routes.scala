package com.orca.ais.viewer.routes

import com.orca.ais.viewer.actors.MessageProcessor.RegisterActor
import com.orca.ais.viewer.actors.{ClientActor, MessageProcessor, WSSinkActor}
import com.orca.ais.viewer.data.DbAccess
import io.prometheus.metrics.core.metrics.Counter
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, DispatcherSelector}
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.{ActorSink, ActorSource}
import org.apache.pekko.stream.{Materializer, OverflowStrategy}
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory

import java.util.UUID

object Routes {

  private val logger = LoggerFactory.getLogger(classOf[Routes.type])

  def getSource()(implicit mat: Materializer): (ActorRef[Message], Publisher[Message]) = {
    ActorSource.actorRef[Message](
      completionMatcher = { case TextMessage.Strict("") => Behaviors.stopped },//PartialFunction.empty,
      failureMatcher = PartialFunction.empty,
      bufferSize = ClientActor.sourceQueueBuffer,
      overflowStrategy = OverflowStrategy.dropHead,
    ).toMat(Sink.asPublisher(fanout = true))(Keep.both) // fanout=false for a single WebSocket connection
      .run() // Materialize to get the ActorRef and the Publisher
  }

  def getSinkAndClientActors(
                             sourceActor: ActorRef[Message],
                             actorSystem: ActorSystem[Nothing],
                             counter: Counter,
                             dbAccess: DbAccess,
                             msgProcessor: ActorRef[MessageProcessor.Command],
                           )
    : (ActorRef[Message], ActorRef[ClientActor.Command]) = {
    val actorsUUID = UUID.randomUUID()
    val clientActor = actorSystem.systemActorOf(
      behavior = ClientActor.getClientActor(ClientActor.State(None, None, sourceActor, counter, dbAccess)),
      name = s"ClientActor$actorsUUID",
      props = DispatcherSelector.fromConfig("orca-ais-viewer.client-dispatcher")
    )
    val wsSinkActor = actorSystem.systemActorOf(WSSinkActor
      .getActor(WSSinkActor.State(clientActor, msgProcessor)), s"WSSinkActor$actorsUUID")
    (wsSinkActor, clientActor)
  }

  def getFlow(msgProcessor: ActorRef[MessageProcessor.Command],
              actorSystem: ActorSystem[Nothing],
              counter: Counter,
              dbAccess: DbAccess,
             )(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    val (sourceActorRef, publisher) = getSource()
    val (wsSinkActor, clientActor) = getSinkAndClientActors(sourceActorRef, actorSystem, counter, dbAccess, msgProcessor)
    val sink = ActorSink.actorRef[Message](
      ref = wsSinkActor,
      onCompleteMessage = TextMessage.Strict(""),
      onFailureMessage = PartialFunction.empty,
    )
    msgProcessor ! RegisterActor(clientActor)
    val flow = Flow.fromSinkAndSource(sink, Source.fromPublisher(publisher))
    flow.watchTermination() { (_, doneFuture) =>
      doneFuture.onComplete { result =>
        msgProcessor ! MessageProcessor.RemoveActor(clientActor)
        clientActor ! ClientActor.PoisonPill
        sourceActorRef ! TextMessage.Strict("")
        wsSinkActor ! TextMessage.Strict("")
        val cause = result.fold(ex => s"failure: ${ex.getMessage}", _ => "Normal completion")
        logger.debug(s"WebSocket flow for ClientActor ${clientActor.path} terminated due to $cause. Stopping associated actors.")
      }(actorSystem.executionContext)
    }
    flow
  }

  def routes(
              msgProcessor: ActorRef[MessageProcessor.Command],
              actorSystem: ActorSystem[Nothing],
              aisMessageCounter: Counter,
              dbAccess: DbAccess,
            )(implicit materializer: Materializer): Route = {
    concat {
      path("getAISData") {
        handleWebSocketMessages(getFlow(msgProcessor, actorSystem, aisMessageCounter, dbAccess))
      }
    }
  }
}
