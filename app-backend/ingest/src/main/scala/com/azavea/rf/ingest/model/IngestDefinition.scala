package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._
import geotrellis.vector.Extent

import java.util.UUID
import java.net.URI

case class IngestDefinition(id: UUID, layers: Array[IngestLayer]) {
  def tasks = for {
    layer <- layers
    source <- layer.sources
    output = layer.output
  } yield IngestDefinition.Task(
    this.id,
    layer.id,
    layer.crs,
    source.uri,
    source.bandMaps,
    source.extent,
    output.uri,
    output.pyramid,
    output.native
  )

  def tasksByLayer = tasks.groupBy(_.layerId)
}

object IngestDefinition {
  implicit val jsonFormat = jsonFormat2(IngestDefinition.apply _)

  case class Task(
    jobId: UUID,
    layerId: UUID,
    crsString: String,
    sourceUri: URI,
    bandMaps: Array[BandMapping],
    extent: Extent,
    outputUri: URI,
    pyramid: Boolean,
    native: Boolean
  )
}

