package com.rasterfoundry.backsplash

import geotrellis.server._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import cats.effect.IO
import org.scalatest._
import org.scalatest.prop.Checkers
import org.scalacheck.Prop.forAll
import com.azavea.maml.ast._

import BacksplashImageGen._
import Implicits._

import scala.concurrent.ExecutionContext


class BacksplashMosaicSpec extends FunSuite with Checkers with Matchers {
  implicit val cs = IO.contextShift(ExecutionContext.global)

  test("remove unnecessary images from mosaic, but not ones we need") {
    check {
      forAll {
        (img1: BacksplashImage, img2: BacksplashImage) =>
          var count = 0
          def work = { count = count + 1 }

          val mosaic = fs2.Stream.emits(List(img1, img2)).repeat.take(50)
          val relevantStream = BacksplashMosaic.filterRelevant(mosaic)

          relevantStream.map({ _ => work }).compile.drain.unsafeRunSync

          if (img1 == img2) {
            assert(count == 1, s"Expected 1, got $count units of work")
          } else {
            assert(count == 2, s"Expected 2, got $count units of work")
          }
          true
      }
    }
  }

  ignore("writeExtent") {
    check {
      forAll {
        (mosaic: BacksplashMosaic) =>
          val eval = mosaic.extentReification
          val e = Extent(-147.34863281250003,20.014645445341365,-83.40820312500001,49.97948776108648)
          val rlit = eval(e, CellSize(10, 10)).unsafeRunSync
          MultibandGeoTiff(rlit.asInstanceOf[RasterLit[Raster[MultibandTile]]].raster, geotrellis.proj4.WebMercator)
            .write(s"/tmp/${java.util.UUID.randomUUID}")
          println("DID ONE")
          true
      }
    }
  }

  test("writeTMS") {
    check {
      forAll {
        (mosaic: BacksplashMosaic) =>
          val eval = mosaic.tmsReification(0)
          val rlit = eval(7, 24, 48).unsafeRunSync
          MultibandGeoTiff(rlit.asInstanceOf[RasterLit[Raster[MultibandTile]]].raster, geotrellis.proj4.WebMercator)
            .write(s"/tmp/${java.util.UUID.randomUUID}.tif")
          println("DID ONE")
          true
      }
    }
  }

}

