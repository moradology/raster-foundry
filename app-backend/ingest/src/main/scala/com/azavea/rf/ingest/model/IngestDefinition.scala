package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._

import java.util.UUID

case class IngestDefinition(id: UUID, layers: Array[IngestLayer])

object IngestDefinition {
  implicit val jsonFormat = jsonFormat2(IngestDefinition.apply _)
}

