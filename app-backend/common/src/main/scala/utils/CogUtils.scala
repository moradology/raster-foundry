package com.azavea.rf.common.utils

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.resample._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.proj4._
import geotrellis.spark.tiling._
import scala.util.Properties
import scala.math
import scala.util.Try

object CogUtils {
  private val TmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  def fetch(uri: String, zoom: Int, x: Int, y: Int): Option[MultibandTile] =
    RangeReaderUtils.fromUri(uri).flatMap { rr =>
      val tiff = GeoTiffReader.readMultiband(rr, decompress = false, streaming = true)
      val transform = Proj4Transform(tiff.crs, WebMercator)
      val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      val tmsTileRE = RasterExtent(
        extent = TmsLevels(zoom).mapTransform.keyToExtent(x, y),
        cols = 256, rows = 256
      )
      val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)

      if (tiffTileRE.extent.intersects(tiff.extent)) {
        val overview = GeoTiffUtils.closestTiffOverview(tiff, tiffTileRE.cellSize, Auto(0))
        val raster = GeoTiffUtils.cropGeoTiff(overview, tiffTileRE.extent)
        Some(raster.tile)
      } else None
    }

  def fetchForExtent(uri: String, zoom: Int, extent: Extent): Option[MultibandTile] =
    RangeReaderUtils.fromUri(uri).flatMap { rr =>
      val tiff = GeoTiffReader.readMultiband(rr, decompress = false, streaming = true)
      val transform = Proj4Transform(tiff.crs, WebMercator)
      val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      val tmsTileRE = RasterExtent(
        extent = extent,
        cellSize = TmsLevels(zoom).cellSize
      )
      val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)

      if (tiffTileRE.extent.intersects(tiff.extent)) {
        val overview = GeoTiffUtils.closestTiffOverview(tiff, tiffTileRE.cellSize, Auto(0))
        val raster = GeoTiffUtils.cropGeoTiff(overview, tiffTileRE.extent)
        Some(raster.tile)
      } else None
    }
}
