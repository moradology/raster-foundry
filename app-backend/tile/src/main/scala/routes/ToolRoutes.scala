package com.azavea.rf.tile.routes

import com.azavea.rf.common.Authentication
import com.azavea.rf.tile.image._
import com.azavea.rf.tile._
import com.azavea.rf.tool.ast._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Tools
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.codec._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.op._

import akka.http.scaladsl.marshalling.Marshal
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.io._
import geotrellis.raster.op._
import geotrellis.raster.render.{Png, ColorRamp, ColorMap}
import geotrellis.raster.io.geotiff._
import geotrellis.vector.Extent
import geotrellis.spark._
import geotrellis.proj4.CRS
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.data.Validated._
import cats.implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.UUID


class ToolRoutes(implicit val database: Database) extends Authentication with LazyLogging {
  val userId: String = "rf_airflow-user"

  def lookupColorMap(str: Option[String]): ColorMap = {
    str match {
      case Some(s) if s.contains(':') =>
        ColorMap.fromStringDouble(s).get
      case None =>
        val colorRamp = ColorRamp(Vector(0xD51D26FF, 0xDD5249FF, 0xE6876CFF, 0xEFBC8FFF, 0xF8F2B2FF, 0xC7DD98FF, 0x96C87EFF, 0x65B364FF, 0x349E4BFF))
        val breaks = Array[Double](0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0)
        ColorMap(breaks, colorRamp)
      case Some(_) =>
        throw new Exception("color map string is all messed up")
    }
  }

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def parseBreakMap(str: String): Map[Double,Double] = {
    str.split(';').map { c: String =>
      val Array(a, b) = c.trim.split(':').map(_.toDouble)
      (a, b)
    }.toMap
  }

  val cachedSource: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]] = { (r, z, x, y) =>
    r match {
      case scene @ SceneRaster(sceneId, Some(band)) =>
        LayerCache.layerTile(sceneId, z, SpatialKey(x, y))
          .map( tile => tile.bands(band)).value

      case scene @ SceneRaster(sceneId, None) =>
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(None)

      case project @ ProjectRaster(projId, Some(band)) =>
        Mosaic.fetch(projId, z, x, y)
          .map( tile => tile.bands(band)).value

      case project @ ProjectRaster(projId, None) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(None)

      case _ =>
        Future.failed(new Exception(s"Cannot handle $r"))
    }
  }

  def root(
    source: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]]
  ): Route =
    pathPrefix(Segment / Segment){ (organizationIdSegment, toolIdSegment) =>
      authenticate { user =>
        // TODO: check token for orgranization access
        val orgId = UUID.fromString(organizationIdSegment)
        val toolId = UUID.fromString(toolIdSegment)
        val futureTool: Future[Tool.WithRelated] = Tools.getTool(toolId, user).map {
          _.getOrElse(throw new java.io.IOException(toolId.toString))
        }

        (pathEndOrSingleSlash & get & rejectEmptyResponse) {
          complete(futureTool)
        } ~
        pathPrefix(IntNumber / IntNumber / IntNumber){ (z, x, y) =>
          parameter(
            'part.?,
            'geotiff.?(false),
            'cm.?
          )
          { (partId, geotiffOutput, colorMap) =>
            complete {
              futureTool.flatMap { tool =>
                logger.debug(s"Raw Tool: $tool")
                // TODO: return useful HTTP errors on parse failure
                val ast = tool.definition.as[MapAlgebraAST] match {
                  case Right(ast) => ast
                  case Left(failure) => throw failure
                }

                // TODO: can we move it outside the z/x/y to get some re-use? (don't think so but should check)
                val tms = Interpreter.tms(ast, source)
                tms(z,x,y).map { op =>
                  op match {
                    case Valid(op) =>
                      val tile = op.toTile(IntCellType).get
                      val png = tile.renderPng(lookupColorMap(colorMap))
                      Future.successful { pngAsHttpResponse(png) }
                    case Invalid(errors) =>
                      Marshal(200 -> errors.toList).to[HttpResponse]
                  }
                }
              }
            }
          }
        }
      }
    }
}
