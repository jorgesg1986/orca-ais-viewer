package com.orca.ais.viewer.actors

import com.orca.ais.viewer.data.DbAccess
import com.orca.ais.viewer.model.{AISStreamMessage, BoundingBoxWithAge, Position}
import com.orca.ais.viewer.utils.json.JsonSupport.PositionFormat
import com.vividsolutions.jts.geom.{Coordinate, Envelope}
import io.prometheus.metrics.core.metrics.Counter
import org.apache.pekko.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.slf4j.LoggerFactory
import spray.json.enrichAny

import scala.concurrent.ExecutionContext

object ClientActor {

  private val logger = LoggerFactory.getLogger(classOf[ClientActor.type])

  sealed trait Command
  case class CheckPosition(aisMessage: AISStreamMessage, timestampReceived: Long) extends Command
  case class UpdateBoundingBox(boundingBox: BoundingBoxWithAge) extends Command
  case object PoisonPill extends Command

  val sourceQueueBuffer = 5000

  case class State(
                    boundingBox: Option[BoundingBoxWithAge],
                    envelope: Option[Envelope],
                    sourceActor: ActorRef[Message],
                    aisMessagesCounter: Counter,
                    dataAccess: DbAccess,
                    ec: ExecutionContext,
                  )

  def getClientActor(state: State): Behavior[Command] = beforeReady(state)

  // Discard AIS messages until we get a bounding box update from the front end
  private def beforeReady(state: State) = {
    Behaviors.receiveMessage[Command] {
      case UpdateBoundingBox(boundingBox) =>
        processUpdateBoundingBox(state, boundingBox)
      case PoisonPill =>
        Behaviors.stopped
      case _ =>
        Behaviors.same
    }
  }

  private def processMessages(state: State): Behaviors.Receive[Command] = {
    Behaviors.receiveMessage[Command] {
      case CheckPosition(positionReport, timestampReceived) =>
        val position = positionReport.toModelPosition(timestampReceived)
        if (checkPositionIsInBoundingBox(state.envelope, position)) {
          try {
            state.aisMessagesCounter.inc()
            val jsonMsg = position.toJson.prettyPrint
            state.sourceActor ! TextMessage.Strict(jsonMsg)
          } catch {
            case e: Exception =>
              logger.error("ClientActor: Error sending message to sourceActor: ", e)
          }
        }
        Behaviors.same
      case UpdateBoundingBox(boundingBox) =>
        processUpdateBoundingBox(state, boundingBox)
      case PoisonPill =>
        Behaviors.stopped
    }
  }

  private def processUpdateBoundingBox(state: State, boundingBox: BoundingBoxWithAge) = {
    val timestampReceivedRequest = System.currentTimeMillis()
    state.dataAccess
      .getAISInfoByLocationAndTimestamp(boundingBox)
      .map { existingVessels =>
        if (existingVessels.nonEmpty)
          logger.info("Sending {} messages to frontend", existingVessels.size)
        existingVessels.foreach { vesselPosition =>
          val jsonMsg = vesselPosition.toModelPosition(timestampReceivedRequest).toJson.prettyPrint
          state.sourceActor ! TextMessage.Strict(jsonMsg)
        }
        state.aisMessagesCounter.inc(existingVessels.size)
      }(state.ec)
    processMessages(state.copy(
      boundingBox = Some(boundingBox),
      envelope = Some(new Envelope(boundingBox.lat1, boundingBox.lat2, boundingBox.long1, boundingBox.long2)),
    ))
  }

  private def checkPositionIsInBoundingBox(envelope: Option[Envelope], position: Position): Boolean = {
    envelope.exists { env =>
      val result = env.contains(new Coordinate(position.lat, position.lon))
      if (result) {
        logger.debug("ClientActor: Checking position for BoundingBox({}). {}", result, position.toJson.prettyPrint)
      }
      result
    }
  }
}
