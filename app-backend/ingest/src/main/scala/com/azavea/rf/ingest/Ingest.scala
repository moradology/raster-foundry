package com.azavea.rf.ingest

import org.apache.spark.rdd._
import org.apache.spark._

import java.net.URI

object Ingest {
  case class Params(jobDefinition: URI = new URI(""))

  def main(args: Array[String]): Unit = {
    val params = CommandLine.parser.parse(args, Ingest.Params()) match {
      case Some(params) => params
      case None => throw new Exception("Unable to parse command line arguments")
    }


    // Some of these options can be set by way of the spark-submit command
    val conf: SparkConf =
      new SparkConf()
        .setAppName(s"Raster Foundry Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")

    // instantiate our spark context (implicit as a convention for functions which require an SC)
    implicit val sc = new SparkContext(conf)

  }
}

