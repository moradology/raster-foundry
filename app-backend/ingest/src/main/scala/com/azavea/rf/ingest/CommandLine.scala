package com.azavea.rf.ingest

import scala.util._
import java.util.HashMap
import java.net.URI
import scala.collection.JavaConversions._

object CommandLine {

	implicit val weekDaysRead: scopt.Read[URI] =
		scopt.Read.reads(new URI(_))

	val parser = new scopt.OptionParser[Ingest.Params]("raster-foundry-ingest") {
		// for debugging; prevents exit from sbt console
		override def terminate(exitState: Either[String, Unit]): Unit = ()

    head("raster-foundry-ingest", "0.1")

		opt[URI]('j',"jobDefinition")
			.action( (jd, conf) => conf.copy(jobDefinition = jd) )
			.text("The location of the json which defines an ingest job")
			.required

  }
}
