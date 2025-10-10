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
import org.apache.pekko.http.impl.util.{ Rendering, ValueRenderable }

/**
 * A websocket extension as defined in http://tools.ietf.org/html/rfc6455#section-4.3
 */
final case class WebSocketExtension(
    name: String, params: immutable.Map[String, String] = Map.empty) extends ValueRenderable {
  def render[R <: Rendering](r: R): r.type = {
    r ~~ name
    if (params.nonEmpty)
      params.foreach {
        case (k, "") => r ~~ "; " ~~ k
        case (k, v)  => r ~~ "; " ~~ k ~~ '=' ~~# v
      }
    r
  }
}
