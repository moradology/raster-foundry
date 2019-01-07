package com.rasterfoundry.backsplash.server

import geotrellis.server._

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.ToolRunDao
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.tool
import com.rasterfoundry.tool.ast.MapAlgebraAST

import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._

import java.util.UUID

object ToolStore {

  private def toolToColorRd(toolRd: tool.RenderDefinition): RenderDefinition = {
    val scaleOpt = toolRd.scale match {
      case tool.Continuous      => Continuous
      case tool.Sequential      => Sequential
      case tool.Diverging       => Diverging
      case tool.Qualitative(fb) => Qualitative(fb)
    }

    val clipOpt = toolRd.clip match {
      case tool.ClipNone  => ClipNone
      case tool.ClipLeft  => ClipLeft
      case tool.ClipRight => ClipRight
      case tool.ClipBoth  => ClipBoth
    }

    RenderDefinition(toolRd.breakpoints, scaleOpt, clipOpt)
  }

  private def unsafeGetAST(analysisId: UUID, nodeId: Option[UUID])(
      implicit xa: Transactor[IO]): IO[MapAlgebraAST] =
    (for {
      executionParams <- ToolRunDao.query.filter(analysisId).select map {
        _.executionParameters
      }
    } yield {
      val decoded = executionParams.as[MapAlgebraAST].toOption getOrElse {
        throw MetadataException(s"Could not decode AST for $analysisId")
      }
      nodeId map {
        decoded
          .find(_)
          .getOrElse {
            throw MetadataException(
              s"Node $nodeId missing from AST $analysisId")
          }
      } getOrElse { decoded }
    }).transact(xa)

  def getTool(analysisId: UUID, nodeId: Option[UUID])(
      implicit xa: Transactor[IO],
      tmsReification: TmsReification[BacksplashMosaic],
      extentReification: ExtentReification[BacksplashMosaic],
      hasRasterExtents: HasRasterExtents[BacksplashMosaic]): IO[PaintableTool] =
    for {
      (expr, mdOption, params) <- unsafeGetAST(analysisId, nodeId) map {
        BacksplashMamlAdapter.asMaml _
      }
    } yield {
      val renderDef = mdOption.flatMap { _.renderDef.map(toolToColorRd) }
      PaintableTool(expr, params, renderDef)
    }

}
