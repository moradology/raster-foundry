package com.azavea.rf.api

import com.azavea.rf.datamodel._

import io.circe._
import cats.syntax.either._

import java.sql.Timestamp
import java.time.Instant

import geotrellis.vector.io._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected
import geotrellis.proj4._
/**
  * Json formats for scenes
  */
//package object scene extends RfJsonProtocols {
//  // implicit val paginatedScenesFormat = jsonFormat6(PaginatedResponse[Scene.WithRelated])
//  implicit def encodePaginated[A: Encoder] =
//    Encoder.forProduct6("count", "hasPrevious", "hasNext", "page", "pageSize", "results")({pr: PaginatedResponse[A] =>
//                                                                                            (pr.count, pr.hasPrevious, pr.hasNext, pr.page, pr.pageSize, pr.results)
//                                                                                          })
//}
