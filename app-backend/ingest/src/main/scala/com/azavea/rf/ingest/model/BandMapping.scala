package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._

case class BandMapping(source: Int, target: Int)

object BandMapping {
  implicit val jsonFormat = jsonFormat2(BandMapping.apply _)
}

