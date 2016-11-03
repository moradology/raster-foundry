package com.azavea.rf.ingest.model


import java.net.URI
import spray.json._
import DefaultJsonProtocol._

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.proj4.CRS

case class SourceDefinition(
  uri: URI,
  extent: Extent,
  crs: CRS,
  cellSize: CellSize,
  bandMaps: Array[BandMapping]
)

object SourceDefinition {
  implicit val jsonFormat = jsonFormat5(SourceDefinition.apply _)
}
