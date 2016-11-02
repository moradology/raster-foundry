package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._

import java.util.UUID

case class IngestLayer(id: UUID, output: OutputDefinition, sources: Array[SourceDefinition], crs: String)

object IngestLayer {
  implicit val jsonFormat = jsonFormat4(IngestLayer.apply _)
}

