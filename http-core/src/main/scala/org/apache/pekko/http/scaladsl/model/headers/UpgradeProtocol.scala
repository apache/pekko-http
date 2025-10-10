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

import org.apache.pekko.http.impl.util.{ Rendering, ValueRenderable }

final case class UpgradeProtocol(name: String, version: Option[String] = None) extends ValueRenderable {
  def render[R <: Rendering](r: R): r.type = {
    r ~~ name
    version.foreach(v => r ~~ '/' ~~ v)
    r
  }
}
