package com.azavea.rf.token

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal

import spray.json.DefaultJsonProtocol

import com.azavea.rf.auth.Authentication
import com.azavea.rf.utils.{Config, UserErrorHandler}


/**
  * Routes for tokens
  */
trait TokenRoutes extends Authentication
    with UserErrorHandler
    with Config
    with SprayJsonSupport
    with DefaultJsonProtocol {

  import com.azavea.rf.AkkaSystem._

  case class DeviceCredential(id: String, device_name: String)
  implicit val deviceCredentialJson = jsonFormat2(DeviceCredential)

  val uri = Uri(s"https://$auth0Domain/api/v2/device-credentials")
  val headers = List(
    Authorization(GenericHttpCredentials("Bearer", auth0Bearer))
  )

  def tokenRoutes: Route = pathPrefix("api" / "tokens") {
    handleExceptions(userExceptionHandler) {
      pathEndOrSingleSlash {
        get {
          authenticate { user =>
            val params = Query(
              "type" -> "refresh_token",
              "user_id" -> user.id
            )
            val req = Http().singleRequest(HttpRequest(GET, uri.withQuery(params), headers))

            onSuccess(req) {
              case HttpResponse(OK, _, entity, _) => complete(Unmarshal(entity).to[List[DeviceCredential]])
              case HttpResponse(errCode, _, _, _) => complete(errCode)
            }
          }
        }
      }
    }
  }
}
