package com.orca.ais.viewer.utils

import com.orca.ais.viewer.model.AISStreamMessage
import magnolify.scalacheck.auto._
import monocle.syntax.all._
import org.scalacheck.Arbitrary

object Utils {
  def getAISStreamMessage(mmsi: Int, lat: Double, lon: Double): AISStreamMessage = {
    val arbAISStreamMessage = implicitly[Arbitrary[AISStreamMessage]]
    val msg = arbAISStreamMessage.arbitrary.sample.get
    msg
      .focus(_.Message.PositionReport.Longitude).replace(lon)
      .focus(_.Message.PositionReport.Latitude).replace(lat)
      .focus(_.MetaData.MMSI).replace(mmsi)
  }
}
