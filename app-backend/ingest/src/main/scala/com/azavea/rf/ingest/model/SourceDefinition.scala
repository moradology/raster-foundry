package com.azavea.rf.ingest.model

import java.net.URI
import spray.json._
import DefaultJsonProtocol._

import geotrellis.vector.Extent

case class SourceDefinition(uri: URI, extent: Extent, bandDefinitions: Array[BandMapping])

object SourceDefinition {
  implicit val jsonFormat = jsonFormat3(SourceDefinition.apply _)
}

