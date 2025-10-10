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

import org.apache.pekko
import pekko.http.impl.util.{ Renderable, Rendering, SingletonValueRenderable }
import pekko.http.javadsl.{ model => jm }

sealed trait ContentDispositionType extends Renderable with jm.headers.ContentDispositionType

object ContentDispositionTypes {
  protected abstract class Predefined extends ContentDispositionType with SingletonValueRenderable {
    def name: String = value
  }

  case object inline extends Predefined
  case object attachment extends Predefined
  case object `form-data` extends Predefined
  final case class Ext(name: String) extends ContentDispositionType {
    def render[R <: Rendering](r: R): r.type = r ~~ name
  }
}
