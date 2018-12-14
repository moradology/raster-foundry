package com.rasterfoundry.backsplash

import org.http4s.server.middleware.Metrics
import org.http4s.metrics.dropwizard.Dropwizard
import com.codahale.metrics.MetricRegistry
import cats.effect.{IO, Clock}

class BacksplashMetrics(implicit clock: Clock[IO]) {
  val registry = new MetricRegistry()

  val middleware = Metrics[IO](Dropwizard[IO](registry, "server")) _

  def timedIO[A](io: IO[A], clazz: Class[_], label: String): IO[A] = {
    val timer = registry.timer(MetricRegistry.name(clazz.getTypeName, label))
    for {
      time <- IO(timer.time())
      theIO <- io
      _ <- IO(time.stop())
    } yield theIO
  }
}
