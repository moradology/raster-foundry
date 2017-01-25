package com.azavea.rf.tile

import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.spark._
import geotrellis.raster.io._
import geotrellis.spark.io._
import geotrellis.spark.io.s3.{S3AttributeStore, S3ValueReader}

import com.github.blemale.scaffeine.{ Cache => ScaffCache, Scaffeine }
import scalacache.caffeine.CaffeineCache
import scalacache.memcached.MemcachedCache
import com.github.benmanes.caffeine.cache._
import com.github.benmanes.caffeine.cache.Caffeine
import scalacache.serialization.InMemoryRepr
import scalacache._
import spray.json.DefaultJsonProtocol._
import net.spy.memcached._
import java.net.InetSocketAddress

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{Executors, TimeUnit}

/**
  * ValueReaders need to read layer metadata in order to know how to decode (x/y) queries into resource reads.
  * In this case it requires reading JSON files from S3, which are cached in the reader.
  * Naturally we want to cache this access to prevent every tile request from re-fetching layer metadata.
  * Same logic applies to other layer attributes like layer Histogram.
  *
  * Things that are cheap to construct but contain internal state we want to re-use use LoadingCache.
  * things that require time to generate, usually a network fetch, use AsyncLoadingCache
  */
object LayerCache extends Config {
  val blockingExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(64))

  val memcachedClient =
    new MemcachedClient(new InetSocketAddress(memcachedHost, memcachedPort))

  implicit val memoryCache: ScalaCache[InMemoryRepr] = {
    val underlyingCaffeineCache =
      Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .expireAfterAccess(cacheExpiration.toMillis, TimeUnit.MILLISECONDS)
        .build[String, Object]
    ScalaCache(CaffeineCache(underlyingCaffeineCache))
  }

  // TODO: Make a scalacache Codec using Kryo
  implicit val memcached: ScalaCache[Array[Byte]] = {
    val client = new MemcachedClient(new InetSocketAddress(memcachedHost, memcachedPort))
    ScalaCache(MemcachedCache(client))
  }

  def attributeStore(bucket: String, prefix: String): Future[S3AttributeStore] =
    caching[S3AttributeStore, InMemoryRepr](s"store-$bucket-$prefix"){
      Future.successful(S3AttributeStore(bucket, prefix))
    }

  def attributeStore(prefix: String): Future[S3AttributeStore] =
    attributeStore(defaultBucket, prefix)

  val futureTiles: ScaffCache[String, Future[Option[MultibandTile]]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(20.minute)
      .maximumSize(500)
      .build[String, Future[Option[MultibandTile]]]()


  def maybeTile(id: RfLayerId, zoom: Int, key: SpatialKey): Future[Option[MultibandTile]] = {

    def fetchTileExpensive: Future[Option[MultibandTile]] =
      for (store <- attributeStore(defaultBucket, id.prefix)) yield {
        val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](id.catalogId(zoom))
        Try(reader.read(key)) match {
          // Only cache failures through failed query
          case Success(tile) => Some(tile)
          case Failure(e: TileNotFoundError) => None
          case Failure(e) => throw e
        }
      }

    def fetchTile(cKey: String) =
      memcachedClient
        .asyncGet(cKey)
        .asFuture[MultibandTile]
        .flatMap({ maybeTile =>
          Option(maybeTile) match {
            case Some(fmtile) => // cache hit
              println(s"tile found in memcached: $fmtile")
              Future { Some(fmtile) }
            case None =>         // cache miss
              println(s"tile cache miss: $cKey")
              val futureMaybeTile = fetchTileExpensive
              for {
                mbTile <- futureMaybeTile
                tile <- mbTile
              } memcachedClient.set(cKey, 10000, tile)
              futureMaybeTile
          }
        })

    val cacheKey = s"tile-$id-$zoom-$key"
    val futureMaybeTile = futureTiles.get(cacheKey, fetchTile)
    futureTiles.put(cacheKey, futureMaybeTile)
    futureMaybeTile
  }

  val futureHistograms: ScaffCache[String, Future[Array[Histogram[Double]]]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(20.minute)
      .maximumSize(500)
      .build[String, Future[Array[Histogram[Double]]]]()


  def bandHistogram(id: RfLayerId, zoom: Int): Future[Array[Histogram[Double]]] = {
    def fetchHistograms(cKey: String) =
      memcachedClient
        .asyncGet(cKey)
        .asFuture[Array[Histogram[Double]]]
        .flatMap({ hists =>
          Option(hists) match {
            case Some(hists) =>
              println(s"hists found in memcached: $hists")
              Future { hists }
            case None =>
              println(s"hists cache miss: $cKey")
              for (store <- attributeStore(defaultBucket, id.prefix))
              yield store.read[Array[Histogram[Double]]](id.catalogId(0), "histogram")
          }
        })

    val cacheKey = s"histogram-$id-$zoom"
    val futHistograms = fetchHistograms(cacheKey)
    futureHistograms.put(cacheKey, futHistograms)
    futHistograms
  }
}
