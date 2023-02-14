/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.http.impl.util.{ ObjectRegistry, SingletonValueRenderable }
import pekko.http.javadsl.{ model => jm }

/** The protocol of an HTTP message */
final case class HttpProtocol private[http] (override val value: String) extends jm.HttpProtocol
    with SingletonValueRenderable

object HttpProtocols extends ObjectRegistry[String, HttpProtocol] {
  private def register(p: HttpProtocol): HttpProtocol = register(p.value, p)

  val `HTTP/1.0` = register(HttpProtocol("HTTP/1.0"))
  val `HTTP/1.1` = register(HttpProtocol("HTTP/1.1"))
  val `HTTP/2.0` = register(HttpProtocol("HTTP/2.0"))
}