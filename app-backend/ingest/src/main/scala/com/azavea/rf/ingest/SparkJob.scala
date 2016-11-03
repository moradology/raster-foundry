package com.azavea.rf.ingest

import org.apache.spark.rdd._
import org.apache.spark._

trait SparkJob {
  // Some of these options can be set by way of the spark-submit command
  val conf: SparkConf =
    new SparkConf()
      .setAppName(s"Raster Foundry Ingest")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")
      .setMaster("local[*]")
  // instantiate our spark context (implicit as a convention for functions which require an SC)
}
