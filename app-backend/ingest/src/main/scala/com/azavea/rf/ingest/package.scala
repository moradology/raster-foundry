package com.azavea.rf

import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.vector._
import geotrellis.util.Component

package object ingest {
  implicit class withRasterFoundryTilerKeyMethods(val self: (ProjectedExtent, Int))
      extends TilerKeyMethods[(ProjectedExtent, Int), (SpatialKey, Int)] {
    def extent = self._1.extent
    def translate(spatialKey: SpatialKey) = (spatialKey, self._2)
  }

  implicit val rfSpatialComponent =
    Component[(SpatialKey, Int), SpatialKey](k => k._1, (sk, k) => (SpatialKey(k.col, k.row), sk._2))
}
