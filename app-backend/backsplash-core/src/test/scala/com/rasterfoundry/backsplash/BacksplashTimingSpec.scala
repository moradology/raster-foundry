package com.rasterfoundry.backsplash

import geotrellis.server._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import cats.data.Validated._
import cats.effect.IO
import cats.implicits._
import org.scalatest._
import org.scalatest.prop.Checkers
import org.scalacheck.Prop.forAll
import com.azavea.maml.ast._

import BacksplashImageGen._
import Implicits._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import java.lang.management.ManagementFactory

class BacksplashForkingThreadPoolTimingSpec
    extends FunSuite
    with Checkers
    with Matchers {
  implicit val cs = IO.contextShift(ExecutionContext.global)
  implicit val t = IO.timer(ExecutionContext.global)
  val mtr = new BacksplashMetrics()

  test("wrapped IO should take ~2 seconds") {
    val work = IO.sleep(2.seconds)
    val newIO = mtr.timedIO(work, classOf[BacksplashForkingThreadPoolTimingSpec], "2secs")
    newIO.unsafeRunSync
    val timer = mtr.registry
      .getTimers()
      .asScala("com.rasterfoundry.backsplash.BacksplashForkingThreadPoolTimingSpec.2secs")
    val snapshot = timer.getSnapshot().getValues().head
    assert(snapshot.nanoseconds.toSeconds == 2,
           "Expected 2 second sleep to take 2 seconds...")
  }

  test("wrapped parallel IO should take ~2 seconds") {
    val printThreads = IO {
      println(ManagementFactory.getThreadMXBean().getThreadCount())
    }
    val ios = ((1 to 10 toList).map { _: Int =>
      IO.sleep(2.seconds)
    }) :+ printThreads
    val parwork = ios.parSequence
    val newIO = mtr.timedIO(parwork, classOf[BacksplashForkingThreadPoolTimingSpec], "2secs_par")
    newIO.unsafeRunSync
    val timer = mtr.registry
      .getTimers()
      .asScala("com.rasterfoundry.backsplash.BacksplashForkingThreadPoolTimingSpec.2secs_par")
    val snapshot = timer.getSnapshot().getValues().head
    assert(snapshot.nanoseconds.toSeconds == 2,
           "Expected 2 second sleep to take 2 seconds...")
  }
}

class BacksplashFixedThreadPoolTimingSpec
    extends FunSuite
    with Checkers
    with Matchers {
  val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit val cs = IO.contextShift(ec)
  implicit val t = IO.timer(ec)
  val mtr = new BacksplashMetrics()

  test("wrapped IO should take ~2 seconds") {
    val work = IO.sleep(2.seconds)
    val newIO = mtr.timedIO(work, classOf[BacksplashFixedThreadPoolTimingSpec], "2secs")
    newIO.unsafeRunSync
    val timer = mtr.registry
      .getTimers()
      .asScala("com.rasterfoundry.backsplash.BacksplashFixedThreadPoolTimingSpec.2secs")
    val snapshot = timer.getSnapshot().getValues().head
    assert(snapshot.nanoseconds.toSeconds == 2,
           "Expected 2 second sleep to take 2 seconds...")
  }
}
