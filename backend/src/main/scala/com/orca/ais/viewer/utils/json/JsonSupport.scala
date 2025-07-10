package com.orca.ais.viewer.utils.json

import com.orca.ais.viewer.model._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object JsonSupport extends DefaultJsonProtocol {
  implicit val AisstreamAuthMessageFormat: RootJsonFormat[AisstreamAuthMessage] = jsonFormat4(AisstreamAuthMessage)
  implicit val PositionReportFormat: RootJsonFormat[PositionReport] = jsonFormat17(PositionReport)
  implicit val PositionFormat: RootJsonFormat[Position] = jsonFormat9(Position)
  implicit val messageFormat: RootJsonFormat[Message] = jsonFormat1(Message)
  implicit val errorMessageFormat: RootJsonFormat[ErrorMessage] = jsonFormat1(ErrorMessage)
  implicit val MetaDataFormat: RootJsonFormat[MetaData] = jsonFormat6(MetaData)
  implicit val AisstreamMessageFormat: RootJsonFormat[AISStreamMessage] = jsonFormat3(AISStreamMessage)
  implicit val BoundingBoxFormat: RootJsonFormat[BoundingBox] = jsonFormat4(BoundingBox)
  implicit val BoundingBoxWithAgeFormat: RootJsonFormat[BoundingBoxWithAge] = jsonFormat5(BoundingBoxWithAge)
}
