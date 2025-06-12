package com.orca.ais.viewer.actors

import com.orca.ais.viewer.data.DbAccess
import com.orca.ais.viewer.model.AISStreamMessage
import io.prometheus.metrics.core.metrics.Counter
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.LoggerFactory

object DbUpdater {

  sealed trait Command
  case class UpsertPosition(message: AISStreamMessage) extends Command

  private val logger = LoggerFactory.getLogger(DbUpdater.getClass)

  def apply(dao: DbAccess, updatedMessages: Counter): Behavior[Command] = processMessages(dao, updatedMessages)

  private def processMessages(dao: DbAccess, updatedMessages: Counter): Behaviors.Receive[Command] = {
    Behaviors.receive[Command] { (context, message) =>
      message match {
        case UpsertPosition(message) =>
          logger.debug(message.toString)
          dao.upsertAISInfo(message.toDataPosition).onComplete { result =>
            logger.debug("Upserted rows: {}", result)
            updatedMessages.inc()
          }(context.executionContext)
          Behaviors.same
      }
    }
  }
}
