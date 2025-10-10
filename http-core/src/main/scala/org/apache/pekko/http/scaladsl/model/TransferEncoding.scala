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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.http.impl.util.{ Renderable, Rendering, SingletonValueRenderable }
import pekko.http.javadsl.{ model => jm }
import pekko.http.impl.util.JavaMapping.Implicits._

sealed abstract class TransferEncoding extends jm.TransferEncoding with Renderable {
  def name: String
  def params: Map[String, String]

  def getParams: java.util.Map[String, String] = params.asJava
}

object TransferEncodings {
  protected abstract class Predefined extends TransferEncoding with SingletonValueRenderable {
    def name: String = value
    def params: Map[String, String] = Map.empty
  }

  case object chunked extends Predefined
  case object compress extends Predefined
  case object deflate extends Predefined
  case object gzip extends Predefined
  case object trailers extends Predefined
  final case class Extension(name: String, params: Map[String, String] = Map.empty) extends TransferEncoding {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ name
      params.foreach { case (k, v) => r ~~ "; " ~~ k ~~ '=' ~~# v }
      r
    }
  }
}
