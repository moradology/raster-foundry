package com.azavea.rf.database.query

import slick.dbio.DBIO

case class ListQueryResult[T](
  records: DBIO[Seq[T]],
  nRecords: DBIO[Int]
)

case class WithRelatedResult2[T1, T2](
  insert1: DBIO[T1],
  insert2: DBIO[Seq[T2]]
)

case class WithRelatedResult3[T1, T2, T3](
  insert1: DBIO[T1],
  insert2: DBIO[Seq[T2]],
  insert3: DBIO[Seq[T3]]
)
