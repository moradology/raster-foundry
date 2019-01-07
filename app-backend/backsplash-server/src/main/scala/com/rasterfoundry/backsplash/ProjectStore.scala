package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.database.SceneToProjectDao
import com.rasterfoundry.datamodel.color.{
  BandGamma => RFBandGamma,
  PerBandClipping => RFPerBandClipping,
  MultiBandClipping => RFMultiBandClipping,
  SigmoidalContrast => RFSigmoidalContrast,
  Saturation => RFSaturation
}
import com.rasterfoundry.backsplash.{BacksplashImage}
import com.rasterfoundry.backsplash.color.{
  ColorCorrect => BSColorCorrect,
  SingleBandOptions => BSSingleBandOptions,
  _
}
import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._
import geotrellis.vector.{Polygon, Projected}

import java.util.UUID

object ProjectStore {
  // safe to get here, since we're just unapplying from a value that we already know
  // was constructed correctly
  @SuppressWarnings(Array("OptionGet"))
  def getProject(
      projId: UUID,
      window: Option[Projected[Polygon]],
      bandOverride: Option[BandOverride],
      imageSubset: Option[NEL[UUID]])(implicit xa: Transactor[IO]) = {
    SceneToProjectDao.getMosaicDefinition(
      projId,
      window,
      bandOverride map { _.red },
      bandOverride map { _.green },
      bandOverride map { _.blue },
      imageSubset map { _.toList } getOrElse List.empty) map { md =>
      val singleBandOptions =
        md.singleBandOptions flatMap {
          _.as[BSSingleBandOptions.Params].toOption
        }
      BacksplashImage(
        md.sceneId,
        md.ingestLocation getOrElse {
          throw UningestedScenesException(
            s"Scene ${md.sceneId} does not have an ingest location")
        },
        md.footprint getOrElse {
          throw MetadataException(
            s"Scene ${md.sceneId} does not have a footprint")
        },
        if (md.isSingleBand) {
          singleBandOptions map { sbo =>
            List(sbo.band)
          } getOrElse {
            throw SingleBandOptionsException(
              "Single band options must be specified for single band projects")
          }
        } else {
          bandOverride map { ovr =>
            List(ovr.red, ovr.green, ovr.blue)
          } getOrElse {
            List(md.colorCorrections.redBand,
                 md.colorCorrections.greenBand,
                 md.colorCorrections.blueBand)
          }
        },
        BSColorCorrect.Params(
          0, // red
          1, // green
          2, // blue
          (BandGamma.apply _)
            .tupled(RFBandGamma.unapply(md.colorCorrections.gamma).get),
          (PerBandClipping.apply _).tupled(
            RFPerBandClipping
              .unapply(md.colorCorrections.bandClipping)
              .get),
          (MultiBandClipping.apply _).tupled(
            RFMultiBandClipping
              .unapply(md.colorCorrections.tileClipping)
              .get),
          (SigmoidalContrast.apply _)
            .tupled(
              RFSigmoidalContrast
                .unapply(md.colorCorrections.sigmoidalContrast)
                .get),
          (Saturation.apply _).tupled(
            RFSaturation
              .unapply(md.colorCorrections.saturation)
              .get)
        ),
        singleBandOptions
      )
    } transact (xa)
  }
}
