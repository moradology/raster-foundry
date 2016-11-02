package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._

import java.net.URI

case class OutputDefinition(uri: URI, pyramid: Boolean, native: Boolean)

object OutputDefinition {
  implicit val jsonFormat = jsonFormat3(OutputDefinition.apply _)
}

