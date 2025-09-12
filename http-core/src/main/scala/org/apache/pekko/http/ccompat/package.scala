/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http

/**
 * INTERNAL API
 */
package object ccompat {

  type Builder[-A, +To] = scala.collection.mutable.Builder[A, To]
}

/**
 * INTERNAL API
 */
package ccompat {
  import org.apache.pekko
  import pekko.http.scaladsl.model.Uri.Query
  trait QuerySeqOptimized extends scala.collection.immutable.LinearSeq[(String, String)]
      with scala.collection.StrictOptimizedLinearSeqOps[(String, String), scala.collection.immutable.LinearSeq, Query] {
    self: Query =>
    override protected def fromSpecific(coll: IterableOnce[(String, String)]): Query =
      Query(coll.iterator.to(Seq): _*)

    override protected def newSpecificBuilder: Builder[(String, String), Query] =
      pekko.http.scaladsl.model.Uri.Query.newBuilder

    override def empty: Query = pekko.http.scaladsl.model.Uri.Query.Empty
  }
}
