package com.orca.ais.viewer.actors

import com.orca.ais.viewer.actors.MessageProcessor.Position
import com.orca.ais.viewer.model.{AISStreamMessage, AisstreamAuthMessage, BoundingBox, ErrorMessage}
import io.prometheus.metrics.core.metrics.Counter
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, Promise}
import scala.util.Try

object AISRetriever {
  import com.orca.ais.viewer.utils.json.JsonSupport._

  type WebSocketClient = (WebSocketRequest, Flow[Message, Message, Any]) => (Future[WebSocketUpgradeResponse], Any)

  sealed trait Command
  case class RetrieveGeometry(msgProcessor: ActorRef[MessageProcessor.Command]) extends Command

  private val logger = LoggerFactory.getLogger(classOf[AISRetriever.type])

  def getActor(apiKey: String, webSocketUri: String, boundingBox: BoundingBox, totalCounter: Counter, singleCounter: Counter)(implicit system: ActorSystem[_]): Behavior[Command] =
    getActorWithWS(apiKey, webSocketUri, boundingBox, totalCounter, singleCounter, Http().singleWebSocketRequest(_, _))

  private[actors] def getActorWithWS(
                                apiKey: String,
                                webSocketUri: String,
                                boundingBox: BoundingBox,
                                totalCounter: Counter,
                                singleCounter: Counter,
                                webSocketClient: WebSocketClient
                              ): Behavior[Command] = {
    Behaviors.receiveMessage {
      case RetrieveGeometry(msgProcessor) =>
        val request = WebSocketRequest(webSocketUri)
        val flowProcessor = buildAISFlow(msgProcessor, apiKey, boundingBox, totalCounter, singleCounter)
        webSocketClient(request, flowProcessor)
        Behaviors.same
    }
  }

  private[actors] def processMessage(
                                      msgProcessor: ActorRef[MessageProcessor.Command],
                                      totalCounter: Counter,
                                      singleCounter: Counter,
                                    ): Message => Unit = {
    case TextMessage.Strict(textMsg) =>
      logger.debug(s"Received text message: $textMsg")
      processMessage(msgProcessor, totalCounter, singleCounter, textMsg)
    case BinaryMessage.Strict(binaryMsg) =>
      val strMsg = binaryMsg.utf8String
      logger.debug(s"Received binary message: $strMsg")
      processMessage(msgProcessor, totalCounter, singleCounter, strMsg)
    case _ =>
      logger.error(s"Received unknown message type")
  }

  def processMessage(msgProcessor: ActorRef[MessageProcessor.Command],
                     totalCounter: Counter,
                     singleCounter: Counter,
                     msg: String): Unit = {
    singleCounter.inc()
    totalCounter.inc()
    Try(Position(JsonParser(msg).convertTo[AISStreamMessage])).fold(
      { _ =>
        val errorMessage = JsonParser(msg).convertTo[ErrorMessage]
        logger.error(s"Error: ${errorMessage.error}")
      },
      { position =>
        msgProcessor ! position
      })
  }

  private[actors] def buildAISFlow(
                                    msgProcessor: ActorRef[MessageProcessor.Command],
                                    apiKey: String,
                                    boundingBox: BoundingBox,
                                    totalCounter: Counter,
                                    singleCounter: Counter,
                                  )
  : Flow[Message, Message, Any] = {
    val res: Flow[Message, Message, Promise[Option[Message]]] = Flow.fromSinkAndSourceMat(
      Sink.foreach[Message](processMessage(msgProcessor, totalCounter, singleCounter)),
      Source.tick(
          initialDelay = 0.seconds,
          interval = 30.seconds,
          tick = buildAuthMessage(apiKey, boundingBox))
        .concatMat(Source.maybe[Message])
        (Keep.right))(Keep.right)
    res
  }

  private[actors] def buildAuthMessage(apiKey: String, boundingBox: BoundingBox): TextMessage = {
    val authMsg: String = AisstreamAuthMessage(
      APIKey = apiKey,
      BoundingBoxes = List(boundingBox.toList),
    ).toJson.prettyPrint
    TextMessage(
      authMsg
    )
  }
}
