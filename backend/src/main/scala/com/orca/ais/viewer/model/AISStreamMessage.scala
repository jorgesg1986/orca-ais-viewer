package com.orca.ais.viewer.model

import com.orca.ais.viewer.data.Position
import com.orca.ais.viewer.model.{Position => ModelPosition}
import com.orca.ais.viewer.utils.geo.GeoUtils
import com.orca.ais.viewer.utils.time.TimeUtils

case class AISStreamMessage(Message: Message, MessageType: String, MetaData: MetaData) {
  def toDataPosition: Position = Position(
    MMSI = MetaData.MMSI,
    name = MetaData.ShipName.strip(),
    lon = Message.PositionReport.Longitude,
    lat = Message.PositionReport.Latitude,
    timestamp = TimeUtils.toInstant(MetaData.time_utc),
    sog = Message.PositionReport.Sog,
    cog = Message.PositionReport.Cog,
    trueHeading = Message.PositionReport.TrueHeading,
    location = GeoUtils.createPoint(Message.PositionReport.Latitude, Message.PositionReport.Longitude)
  )

  def toModelPosition(timestampReceived: Long): ModelPosition = ModelPosition(
    MMSI = MetaData.MMSI,
    name = MetaData.ShipName.strip(),
    lon = Message.PositionReport.Longitude,
    lat = Message.PositionReport.Latitude,
    timestamp = MetaData.time_utc,
    sog = Message.PositionReport.Sog,
    cog = Message.PositionReport.Cog,
    trueHeading = Message.PositionReport.TrueHeading,
    timestampReceived = timestampReceived,
  )
}
