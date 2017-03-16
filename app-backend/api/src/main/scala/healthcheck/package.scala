package com.azavea.rf.api

import io.circe._
import io.circe.syntax._

/**
  * Json formats for healthcheck case classes
  *
  */
package object healthcheck {

  implicit val HealthCheckEncoder: Encoder[HealthCheck] =
    Encoder.encodeString.contramap[HealthCheck]({ hc => hc.toString })

  //implicit object HealthCheckStatusJsonFormat extends JsonFormat[HealthCheckStatus.Status] {
  //  def write(obj: HealthCheckStatus.Status): JsValue = JsString(obj.toString)
  //  def read(json: JsValue): HealthCheckStatus.Status = json match {
  //    case JsString(str) => HealthCheckStatus.withName(str)
  //    case _ => throw new DeserializationException("Enum string expected")
  //  }
  //}

  //implicit val serviceCheckFormat = jsonFormat2(ServiceCheck)
  //implicit val healthCheckFormat = jsonFormat2(HealthCheck)
}
