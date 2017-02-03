package com.azavea.rf.datamodel

abstract class WithRelated2Constructor {
  type T1
  type T2
  type Related[T1]

  def withRelatedFromComponents(s: Seq[T2]): Related[T1]
}
