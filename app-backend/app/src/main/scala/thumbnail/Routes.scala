package com.azavea.rf.thumbnail

import java.util.UUID

import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.common.{UserErrorHandler, Authentication}
import com.azavea.rf.database.tables.Thumbnails
import com.azavea.rf.database.{Database, ActionRunner}
import com.azavea.rf.datamodel._


trait ThumbnailRoutes extends Authentication
    with ThumbnailQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler
    with ActionRunner {

  implicit def database: Database

  val thumbnailRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listThumbnails } ~
      post { createThumbnail }
    } ~
    pathPrefix(JavaUUID) { thumbnailId =>
      pathEndOrSingleSlash {
        get { getThumbnail(thumbnailId) } ~
        put { updateThumbnail(thumbnailId) } ~
        delete { deleteThumbnail(thumbnailId) }
      }
    }
  }

  def listThumbnails: Route = authenticate { user =>
    (withPagination & thumbnailSpecificQueryParameters) { (page, thumbnailParams) =>
      complete {
        list[Thumbnail](Thumbnails.listThumbnails(page, thumbnailParams),
                        page.offset,
                        page.limit)
      }
    }
  }

  def createThumbnail: Route = authenticate { user =>
    entity(as[Thumbnail.Create]) { newThumbnail =>
      onSuccess(
        write[Thumbnail](
          Thumbnails.insertThumbnail(newThumbnail.toThumbnail)
        )
      ) { thumbnail =>
        complete(StatusCodes.Created, thumbnail)
      }
    }
  }

  def getThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    withPagination { page =>
      rejectEmptyResponse {
        complete {
          readOne[Thumbnail](Thumbnails.getThumbnail(thumbnailId))
        }
      }
    }
  }

  def updateThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    entity(as[Thumbnail]) { updatedThumbnail =>
      onSuccess(
        update(Thumbnails.updateThumbnail(updatedThumbnail, thumbnailId))
      ) {
        case 1 => complete(StatusCodes.NoContent)
        case 0 => complete(StatusCodes.NotFound)
        case count => throw new IllegalStateException(
          s"Error updating thumbnail: update result expected to be 1, was $count"
        )
      }
    }
  }

  def deleteThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    onSuccess(drop(Thumbnails.deleteThumbnail(thumbnailId))) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting thumbnail: delete result expected to be 1, was $count"
      )
    }
  }
}
