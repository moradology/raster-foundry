package com.azavea.rf.database

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import slick.lifted.Rep
import slick.dbio.{DBIO, StreamingDBIO, Streaming}

import com.azavea.rf.datamodel._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api.TableQuery
import com.azavea.rf.database.query._

object Test {

  trait HasRelations[A] {
    type Arg
    type Out
    def relates(self: A, other: Arg): Out
  }

  object HasRelations {
    type Aux[In, Arg0, Out0] = HasRelations[In] { type Arg = Arg0; type Out = Out0 }

    def apply[A, B, C](f: (A, B) => C): HasRelations.Aux[A, B, C] =
      new HasRelations[A] {
        type Arg = B
        type Out = C
        def relates(self: A, related: Arg): Out = f(self, related)
      }

    implicit val ImageRelatesBand = HasRelations {
      (self: Image, related: Seq[Band]) => Image.WithRelated(
        self.id,
        self.createdAt,
        self.modifiedAt,
        self.organizationId,
        self.createdBy,
        self.modifiedBy,
        self.rawDataBytes,
        self.visibility,
        self.filename,
        self.sourceUri,
        self.scene,
        self.imageMetadata,
        self.resolutionMeters,
        self.metadataFiles,
        related
      )
    }

    implicit class withRelateMethod[A](val self: A) {
      def relates[B, C](related: B)(implicit ev: HasRelations.Aux[A, B, C]): C = ev.relates(self, related)
    }

    val bands = ???.asInstanceOf[Seq[Band]]
    val x = ???.asInstanceOf[Image].relates(bands)
  }
}


trait ActionRunner {

  def list[T](a: ListQueryResult[T], offset: Int, limit: Int)
          (implicit database: DB): Future[PaginatedResponse[T]] = {
    database.db.run(for {
      records <- a.records
      nRecords <- a.nRecords
    } yield {
      PaginatedResponse[T](
        nRecords,
        offset > 0,
        (offset + 1) * limit < nRecords,
        offset,
        limit,
        records
      )
    })
  }

  def readOne[T](a: DBIO[Option[T]])(implicit database: DB): Future[Option[T]] =
    database.db.run(a)

  def write[T](a: DBIO[T])(implicit database: DB): Future[T] = database.db.run(a)

  def update(a: DBIO[Int])(implicit database: DB): Future[Int] = database.db.run(a)

  def drop(a: DBIO[Int])(implicit database: DB): Future[Int] = database.db.run(a)

  def withRelatedInsert2[T : Test.HasRelations, B, C](a: DBIO[(T, B)])
                        (implicit ev: Test.HasRelations.Aux[T, B, C], database: DB): Future[C] = {
    database.db.run(a) map {
      case (r1, r2) => ev.relates(r1, r2)
    }
  }
}
