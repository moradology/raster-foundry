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

  def main(args: Array[String]): Unit = {
    val params = CommandLine.parser.parse(args, Ingest.Params()) match {
      case Some(params) => params
      case None => throw new Exception("Unable to parse command line arguments")
    }

    val ingestDefinition = readString(params.jobDefinition).parseJson.convertTo[IngestDefinition]

    val tasksByLayer = ingestDefinition.tasksByLayer

    // Loop over the different Layers to construct RDDs from their input sources
    tasksByLayer.foreach { case (id, tasks) =>
      val tasksRdd: RDD[IngestDefinition.Task] = sc.parallelize(tasks)

      val preLayout: RDD[((ProjectedExtent, Int), Tile)] =
        tasksRdd.flatMap { task: IngestDefinition.Task =>
          val tiffBytes = readBytes(task.sourceUri)
          val MultibandGeoTiff(mbTile, srcExtent, crs, tags, options) = MultibandGeoTiff(tiffBytes)
          val projected = mbTile.reproject(srcExtent, crs, LatLng)

          task.bandMaps.map { bandMap: BandMapping =>
            ((ProjectedExtent(srcExtent, LatLng), bandMap.target), mbTile.band(bandMap.source))
          }
        }

      val destCRS = CRS.fromString(tasks.head.crsString)

      val overallExtent = tasks
        .map(_.extent)
        .reduce({ (overallExtent, nextExtent) =>
          overallExtent.expandToInclude(nextExtent)
        }).reproject(LatLng, destCRS)

      val layoutScheme = ZoomedLayoutScheme.layoutForZoom(15, destCRS.worldExtent, 256)

      val GridBounds(colMin, rowMin, colMax, rowMax) = layoutScheme.mapTransform(overallExtent)
      val tlmd = 
        TileLayerMetadata(
          cellType = UShortCellType,
          layout = layoutScheme,
          extent = overallExtent,
          crs = destCRS,
          bounds = KeyBounds(
            SpatialKey(colMin, rowMin),
            SpatialKey(colMax, rowMax)
          )
        )

      //val tlmd: TileLayerMetadata[(SpatialKey, Int)] =
      //  TileLayerMetadata.fromRdd(preLayout, ZoomedLayoutScheme(LatLng), Some(20))

      preLayout.tileToLayout(tlmd)

    }
  }
}

