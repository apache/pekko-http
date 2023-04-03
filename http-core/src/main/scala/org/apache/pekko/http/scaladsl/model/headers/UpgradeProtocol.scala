/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model.headers

import org.apache.pekko.http.impl.util.{ Rendering, ValueRenderable }

final case class UpgradeProtocol(name: String, version: Option[String] = None) extends ValueRenderable {
  def render[R <: Rendering](r: R): r.type = {
    r ~~ name
    version.foreach(v => r ~~ '/' ~~ v)
    r
  }
}
