package com.azavea.rf.ingest

import org.apache.spark.rdd._
import org.apache.spark._
import spray.json._
import geotrellis.proj4._
import geotrellis.vector.ProjectedExtent
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.proj4.LatLng

import java.net.URI

import com.azavea.rf.ingest.util._
import com.azavea.rf.ingest.model._

object Ingest extends SparkJob {

  case class Params(jobDefinition: URI = new URI(""))

  def calculateTileLayerMetadata(layer: IngestLayer): TileLayerMetadata[SpatialKey] = {
    // We need to build TileLayerMetadata that we expect to start pyramid from
    val overallExtent = layer.sources
      .map(src => src.extent.reproject(src.crs, layer.output.crs))
      .reduce(_ combine _)

    // Infer the base level of the TMS pyramid based on overall extent and cellSize
    val LayoutLevel(maxZoom, baseLayoutDefinition) =
      ZoomedLayoutScheme(layer.output.crs, 256).levelFor(overallExtent, layer.output.cellSize)

    TileLayerMetadata(
      cellType = layer.output.cellType,
      layout = baseLayoutDefinition,
      extent = overallExtent,
      crs = layer.output.crs,
      bounds = {
        val GridBounds(colMin, rowMin, colMax, rowMax) =
          baseLayoutDefinition.mapTransform(overallExtent)
        KeyBounds(
          SpatialKey(colMin, rowMin),
          SpatialKey(colMax, rowMax)
        )
      }
    )
  }

  def main(args: Array[String]): Unit = {
    val params = CommandLine.parser.parse(args, Ingest.Params()) match {
      case Some(params) => params
      case None => throw new Exception("Unable to parse command line arguments")
    }

    val ingestDefinition = readString(params.jobDefinition).parseJson.convertTo[IngestDefinition]

    implicit val sc = new SparkContext(conf)

    // Loop over the different Layers to construct RDDs from their input sources
    ingestDefinition.layers.foreach { layer =>
      val destCRS = layer.output.crs

      // Read source tiles and reproject them to desired CRS
      val sourceTiles: RDD[((ProjectedExtent, Int), Tile)] =
        sc.parallelize(layer.sources, layer.sources.length).flatMap { source =>
          val srcCrs = source.crs
          val tiffBytes = readBytes(source.uri)
          val MultibandGeoTiff(mbTile, srcExtent, _, tags, options) = MultibandGeoTiff(tiffBytes)

          source.bandMaps.map { bm: BandMapping =>
            // GeoTrellis multi-band tiles are 0 indexed
            val band = mbTile.band(bm.source - 1).reproject(srcExtent, srcCrs, destCRS)
            (ProjectedExtent(band.extent, destCRS), bm.target - 1) -> band.tile
          }
        }

      val tileLayerMetadata = Ingest.calculateTileLayerMetadata(layer)

      // TODO: Add resample options to use Bilinear/NN (needs to be job layer.output)

      val tiledRdd = sourceTiles.tileToLayout[(SpatialKey, Int)](
        tileLayerMetadata.cellType,
        tileLayerMetadata.layout
      )
      tiledRdd.keys.collect.foreach(println)
    }
    sc.stop
  }
  // test:runMain com.azavea.rf.ingest.Ingest -j file:/Users/eugene/proj/raster-foundry/app-backend/ingest/sampleJob.json
}
