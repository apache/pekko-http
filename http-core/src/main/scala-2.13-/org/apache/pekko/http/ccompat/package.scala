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

/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.apache.pekko.http

import org.apache.pekko
import scala.collection.generic.{ CanBuildFrom, GenericCompanion }
import scala.collection.{ mutable, GenTraversable }
import scala.{ collection => c }

/**
 * INTERNAL API
 *
 * Partly based on https://github.com/scala/scala-collection-compat/blob/main/compat/src/main/scala-2.11_2.12/scala/collection/compat/PackageShared.scala
 * but reproduced here so we don't need to add a dependency on this library. It contains much more than we need right now, and is
 * not promising binary compatibility yet at the time of writing.
 */
package object ccompat {
  import CompatImpl._

  implicit def genericCompanionToCBF[A, CC[X] <: GenTraversable[X]](
      fact: GenericCompanion[CC]): CanBuildFrom[Any, A, CC[A]] =
    simpleCBF(fact.newBuilder[A])

  // This really belongs into scala.collection but there's already a package object
  // in scala-library so we can't add to it
  type IterableOnce[+X] = c.TraversableOnce[X]
  val IterableOnce = c.TraversableOnce

  implicit class RichQueue[T](val queue: mutable.Queue[T]) extends AnyVal {
    // missing in 2.12
    def -=(element: T): Unit = queue.dequeueAll(_ == element)
  }

  object JavaConverters extends scala.collection.convert.DecorateAsJava with scala.collection.convert.DecorateAsScala
}

/**
 * INTERNAL API
 */
package ccompat {
  trait Builder[-Elem, +To] extends mutable.Builder[Elem, To] { self =>
    // This became final in 2.13 so cannot be overridden there anymore
    final override def +=(elem: Elem): this.type = addOne(elem)
    def addOne(elem: Elem): this.type = self.+=(elem)
  }

  trait QuerySeqOptimized extends scala.collection.immutable.LinearSeq[(String, String)]
      with scala.collection.LinearSeqOptimized[(String, String), pekko.http.scaladsl.model.Uri.Query] {
    self: pekko.http.scaladsl.model.Uri.Query =>
    override def newBuilder: mutable.Builder[(String, String), pekko.http.scaladsl.model.Uri.Query] =
      pekko.http.scaladsl.model.Uri.Query.newBuilder
  }
}
