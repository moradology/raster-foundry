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

  case class AuthorizedToken(id_token: String, expires_in: Int, token_type: String)
  implicit val authorizedTokenJson = jsonFormat3(AuthorizedToken)

  case class RefreshToken(refresh_token: String)
  implicit val refreshTokenJson = jsonFormat1(RefreshToken)

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
        } ~
        post {
          entity(as[RefreshToken]) { rt =>
            val params = FormData(
              "api_type" -> "app",
              "grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer",
              "scope" -> "openid",
              "refresh_token" -> rt.refresh_token,
              "client_id" -> auth0ClientId,
              "target" -> auth0ClientId
            ).toEntity
            val req = Http().singleRequest(HttpRequest(
              method = POST,
              uri = uri.withPath(Path("/delegation")),
              entity = params
            ))

            onSuccess(req) {
              case HttpResponse(OK, _, entity, _) => complete(Unmarshal(entity).to[AuthorizedToken])
              case HttpResponse(errCode, _, _, _) => complete(errCode)
            }
          }
        }
      } ~
      path(".*".r) { deviceId =>
        delete {
          authenticate { user =>
            // TODO Check that the deviceId belongs to user before deleting it!
            // Otherwise any authenticated user will be able to delete any
            // device key, whether or not they own it.

            val devicePath = s"$uri/$deviceId"
            val req = Http().singleRequest(HttpRequest(DELETE, devicePath, headers))

            onSuccess(req) {
              case HttpResponse(resCode, _, _, _) => complete(resCode)
            }
          }
        }
      }
    }
  }
}
