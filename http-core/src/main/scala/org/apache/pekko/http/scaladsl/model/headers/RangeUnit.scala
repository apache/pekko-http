/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model.headers

import org.apache.pekko
import pekko.http.impl.util.{ Rendering, ValueRenderable }
import pekko.http.javadsl.{ model => jm }

sealed abstract class RangeUnit extends jm.headers.RangeUnit with ValueRenderable {
  def name: String
}

object RangeUnits {
  case object Bytes extends RangeUnit {
    def name = "Bytes"

    def render[R <: Rendering](r: R): r.type = r ~~ "bytes"
  }

  final case class Other(name: String) extends RangeUnit {
    def render[R <: Rendering](r: R): r.type = r ~~ name
  }
}
