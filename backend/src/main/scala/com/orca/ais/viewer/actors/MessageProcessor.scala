package com.orca.ais.viewer.actors

import com.orca.ais.viewer.actors.ClientActor.CheckPosition
import com.orca.ais.viewer.actors.DbUpdater.UpsertPosition
import com.orca.ais.viewer.model.AISStreamMessage
import io.prometheus.metrics.core.metrics.{Counter, Gauge}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.slf4j.LoggerFactory

object MessageProcessor {

  private val logger = LoggerFactory.getLogger(classOf[MessageProcessor.type])

  sealed trait Command
  case class Position(positionReport: AISStreamMessage, timestampReceived: Long = System.currentTimeMillis()) extends Command
  case class RegisterActor(clientActor: ActorRef[ClientActor.Command]) extends Command
  case class RemoveActor(clientActor: ActorRef[ClientActor.Command]) extends Command

  case class MessageProcessorState(
                                    clientActors: Set[ActorRef[ClientActor.Command]] = Set(),
                                    dbUpdater: ActorRef[DbUpdater.Command],
                                    messagesProcessedCounter: Counter,
                                    registeredClientActorsGauge: Gauge,
                                  )

  def apply(state: MessageProcessorState): Behavior[Command] = {
        Behaviors.receiveMessage[Command] {
          case RegisterActor(clientActor) =>
            val newSet = state.clientActors + clientActor
            state.registeredClientActorsGauge.inc()
            logger.debug(s"Registering new client actor: ${clientActor.path} Total actors: ${newSet.size}")
            apply(state.copy(clientActors = state.clientActors + clientActor))
          case RemoveActor(clientActor) =>
            state.registeredClientActorsGauge.dec()
            apply(state.copy(clientActors = state.clientActors - clientActor))
          case Position(positionReport, timestampReceived) =>
            state.dbUpdater ! UpsertPosition(positionReport)
            state.messagesProcessedCounter.inc()
            state.clientActors.foreach { clientActor => clientActor ! CheckPosition(positionReport, timestampReceived) }
            Behaviors.same
        }
  }
}
