package com.orca.ais.viewer.actors

import com.orca.ais.viewer.actors.ClientActor.UpdateBoundingBox
import com.orca.ais.viewer.actors.MessageProcessor.RemoveActor
import com.orca.ais.viewer.model.BoundingBoxWithAge
import com.orca.ais.viewer.utils.json.JsonSupport.BoundingBoxWithAgeFormat
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import org.slf4j.LoggerFactory
import spray.json.JsonParser

object WSSinkActor {

  private val logger = LoggerFactory.getLogger(WSSinkActor.getClass)

  case class State(
                    clientActor: ActorRef[ClientActor.Command],
                    messageProcessorActor: ActorRef[MessageProcessor.Command],
                  )

  def getActor(state: State): Behavior[Message] = {
    Behaviors.receiveMessage[Message] {
      case TextMessage.Strict(msg) =>
        logger.debug(s"Received text message: $msg")
        if(msg.nonEmpty) {
          state.clientActor ! UpdateBoundingBox(JsonParser(msg).convertTo[BoundingBoxWithAge])
          Behaviors.same
        } else {
          //state.messageProcessorActor ! RemoveActor(state.clientActor)
          //Behaviors.stopped
          Behaviors.same
        }
      case BinaryMessage.Strict(msg) =>
        val strMsg = msg.utf8String
        logger.debug(s"Received binary message: $strMsg")
        state.clientActor ! UpdateBoundingBox(JsonParser(strMsg).convertTo[BoundingBoxWithAge])
        Behaviors.same
      case msg =>
        logger.error(s"Unknown message type received (Probably Streamed): $msg")
        Behaviors.same
    }
  }

}
