package com.orca.ais.viewer.model

// class to be serialized to JSON to be sent to aisstream.io
case class AisstreamAuthMessage(
                                 APIKey: String,
                                 BoundingBoxes: List[List[List[Double]]],
                                 FiltersShipMMSI: List[String] = List(),
                                 FilterMessageTypes: List[String] = List("PositionReport"),
                               )
