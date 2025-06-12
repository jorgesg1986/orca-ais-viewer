package com.orca.ais.viewer.data

import com.orca.ais.viewer.data.OrcaPostgresProfile.api._
import com.orca.ais.viewer.model.{Position => ModelPosition}
import com.vividsolutions.jts.geom.Point

import java.time.Instant

case class Position(
                     MMSI: Int,
                     name: String,
                     lon: Double,
                     lat: Double,
                     location: Point,
                     cog: Double,
                     trueHeading: Double,
                     sog: Double,
                     timestamp: Instant,
                   ) {
  def toModelPosition(timestampReceived: Long): ModelPosition = ModelPosition(
    MMSI = MMSI,
    name = name,
    lon = lon,
    lat = lat,
    timestamp = timestamp.toString,
    sog = sog,
    cog = cog,
    trueHeading = trueHeading,
    timestampReceived = timestampReceived,
  )
}

class PositionTable(tag: Tag) extends Table[Position](tag, "position") {
  def MMSI = column[Int]("MMSI", O.PrimaryKey)
  def name = column[String]("name")
  def lon = column[Double]("lon")
  def lat = column[Double]("lat")
  def location = column[Point]("location")
  def cog = column[Double]("cog")
  def trueHeading = column[Double]("trueHeading")
  def sog = column[Double]("sog")
  def timestamp = column[Instant]("timestamp")

  def * =
    (MMSI, name, lon, lat, location, cog, trueHeading, sog, timestamp) <> (Position.tupled, Position.unapply)
}
