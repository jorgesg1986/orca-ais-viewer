package com.orca.ais.viewer.data

import com.orca.ais.viewer.data.OrcaPostgresProfile.api._
import com.orca.ais.viewer.model.BoundingBoxWithAge
import org.slf4j.LoggerFactory
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api.Database
import slick.sql.FixedSqlStreamingAction

import java.time.Instant
import scala.concurrent.Future

class DbAccess(db: Database) {
  private val positions = TableQuery[PositionTable]
  private val logger = LoggerFactory.getLogger(classOf[DbAccess])

  def upsertAISInfo(positionList: List[Position]): Future[Option[Int]] = {
    db.run(positions.insertOrUpdateAll(positionList))
  }

  def getAISInfoByLocationAndTimestamp(boundingBox: BoundingBoxWithAge, timestamp: Instant = Instant.now()): Future[Seq[Position]] = {

    val envelope = makeEnvelope(boundingBox.lat1, boundingBox.long1, boundingBox.lat2, boundingBox.long2, Some(4326))

    val query: FixedSqlStreamingAction[Seq[Position], Position, Effect.Read] =
      positions.filter(row =>
        row.location.within(envelope) &&
          row.timestamp >= timestamp.minusSeconds((boundingBox.age * 60).toLong))
        .result

    db.run(query)
  }
}
