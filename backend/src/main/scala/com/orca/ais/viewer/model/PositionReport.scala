package com.orca.ais.viewer.model

case class PositionReport(
                           MessageID: Int,
                           RepeatIndicator: Int, 
                           UserID: Int, 
                           Valid: Boolean, 
                           NavigationalStatus: Int, 
                           RateOfTurn: Int,
                           Sog: Double,
                           PositionAccuracy: Boolean,
                           Longitude: Double,
                           Latitude: Double,
                           Cog: Double,
                           TrueHeading: Int,
                           Timestamp: Int,
                           SpecialManoeuvreIndicator: Int,
                           Spare: Int,
                           Raim: Boolean,
                           CommunicationState: Int,
                         )
