package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._

import java.util.UUID

case class IngestLayer(
  id: UUID,
  output: OutputDefinition,
  sources: Array[SourceDefinition]
)

object IngestLayer {
  implicit val jsonFormat = jsonFormat3(IngestLayer.apply _)
}
