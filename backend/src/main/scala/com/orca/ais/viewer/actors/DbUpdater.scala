package com.orca.ais.viewer.actors

import com.orca.ais.viewer.data.{DbAccess, Position}
import com.orca.ais.viewer.model.AISStreamMessage
import io.prometheus.metrics.core.metrics.Counter
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.util.{Failure, Success}

object DbUpdater {

  sealed trait Command
  case class UpsertPosition(message: AISStreamMessage) extends Command

  private val logger = LoggerFactory.getLogger(DbUpdater.getClass)

  def apply(dao: DbAccess, updatedMessages: Counter, batchSize: Int): Behavior[Command] = processMessages(dao, updatedMessages, batchSize, new MessageBatcher())

  private def processMessages(dao: DbAccess, updatedMessages: Counter, batchSize: Int, batcher: MessageBatcher): Behaviors.Receive[Command] = {
    Behaviors.receive[Command] { (context, message) =>
      message match {
        case UpsertPosition(message) =>
          if (batcher.size > batchSize) {
            dao.upsertAISInfo(batcher.getBatch.toList :+ message.toDataPosition).onComplete {
              case Failure(throwable) =>
                logger.error(throwable.getMessage)
              case Success(rows) =>
                rows.map{ numberOfRows =>
                  logger.debug("Upserted rows: {}", numberOfRows)
                  updatedMessages.inc(numberOfRows)
                }
            }(context.executionContext)
            batcher.emptyBatch()
          } else {
            batcher.addPosition(message.toDataPosition)
          }

          Behaviors.same
      }
    }
  }
}

class MessageBatcher {
  private var batch: mutable.ArraySeq[Position] = mutable.ArraySeq[Position]()

  def addPosition(position: Position): Unit = {
    batch = batch :+ position
  }

  def getBatch: mutable.ArraySeq[Position] = batch

  def emptyBatch(): Unit = {
    batch = batch.empty
  }

  def size: Int = batch.length
}
