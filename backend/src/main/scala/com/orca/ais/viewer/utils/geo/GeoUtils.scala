package com.orca.ais.viewer.utils.geo

import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, Point}

object GeoUtils {

  private val geoFactory = new GeometryFactory()

  def createPoint(lat: Double, long: Double): Point = geoFactory.createPoint(new Coordinate(lat, long))
}
