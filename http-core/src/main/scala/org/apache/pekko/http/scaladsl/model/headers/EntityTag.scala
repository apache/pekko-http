/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model.headers

import scala.collection.immutable
import org.apache.pekko
import pekko.http.impl.util.{ Renderer, Rendering, ValueRenderable }
import pekko.http.javadsl.{ model => jm }

final case class EntityTag(tag: String, weak: Boolean = false) extends jm.headers.EntityTag with ValueRenderable {
  def render[R <: Rendering](r: R): r.type = if (weak) r ~~ "W/" ~~#! tag else r ~~#! tag
}

object EntityTag {
  def matchesRange(eTag: EntityTag, entityTagRange: EntityTagRange, weakComparison: Boolean) =
    entityTagRange match {
      case EntityTagRange.`*`           => weakComparison || !eTag.weak
      case EntityTagRange.Default(tags) => tags.exists(matches(eTag, _, weakComparison))
    }
  def matches(eTag: EntityTag, other: EntityTag, weakComparison: Boolean) =
    other.tag == eTag.tag && (weakComparison || !other.weak && !eTag.weak)
}

sealed abstract class EntityTagRange extends jm.headers.EntityTagRange with ValueRenderable

object EntityTagRange {
  def apply(tags: EntityTag*) = Default(immutable.Seq(tags: _*))

  implicit val tagsRenderer: Renderer[immutable.Iterable[EntityTag]] = Renderer.defaultSeqRenderer[EntityTag] // cache

  case object `*` extends EntityTagRange {
    def render[R <: Rendering](r: R): r.type = r ~~ '*'
  }

  final case class Default(tags: immutable.Seq[EntityTag]) extends EntityTagRange {
    require(tags.nonEmpty, "tags must not be empty")
    def render[R <: Rendering](r: R): r.type = r ~~ tags
  }

}
