package com.orca.ais.viewer.model

case class Position(
                     MMSI: Int,
                     name: String,
                     lon: Double,
                     lat: Double,
                     cog: Double,
                     trueHeading: Double,
                     sog: Double,
                     timestamp: String,
                     timestampReceived: Long,
                   )
