package com.orca.ais.viewer.model

case class BoundingBox(lat1: Double, long1: Double, lat2: Double, long2: Double) {
  def toList: List[List[Double]] = List(List(lat1, long1), List(lat2, long2))
}

case class BoundingBoxWithAge(lat1: Double, long1: Double, lat2: Double, long2: Double, age: Int)
