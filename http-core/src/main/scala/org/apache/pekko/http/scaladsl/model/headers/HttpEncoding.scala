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

import language.implicitConversions
import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.javadsl.{ model => jm }
import pekko.http.scaladsl.model.WithQValue
import pekko.http.impl.util.JavaMapping.Implicits._

sealed abstract class HttpEncodingRange extends jm.headers.HttpEncodingRange with ValueRenderable
    with WithQValue[HttpEncodingRange] {
  def qValue: Float
  def matches(encoding: HttpEncoding): Boolean

  /** Java API */
  def matches(encoding: jm.headers.HttpEncoding): Boolean = matches(encoding.asScala)
}

object HttpEncodingRange {
  case class `*`(qValue: Float) extends HttpEncodingRange {
    require(0.0f <= qValue && qValue <= 1.0f, "qValue must be >= 0 and <= 1.0")
    final def render[R <: Rendering](r: R): r.type = if (qValue < 1.0f) r ~~ "*;q=" ~~ qValue else r ~~ '*'
    def matches(encoding: HttpEncoding) = true
    def withQValue(qValue: Float) =
      if (qValue == 1.0f) `*` else if (qValue != this.qValue) `*`(qValue.toFloat) else this
  }
  object `*` extends `*`(1.0f)

  final case class One(encoding: HttpEncoding, qValue: Float) extends HttpEncodingRange {
    require(0.0f <= qValue && qValue <= 1.0f, "qValue must be >= 0 and <= 1.0")
    def matches(encoding: HttpEncoding) = this.encoding.value.equalsIgnoreCase(encoding.value)
    def withQValue(qValue: Float) = One(encoding, qValue)
    def render[R <: Rendering](r: R): r.type = if (qValue < 1.0f) r ~~ encoding ~~ ";q=" ~~ qValue else r ~~ encoding
  }

  implicit def apply(encoding: HttpEncoding): HttpEncodingRange = apply(encoding, 1.0f)
  def apply(encoding: HttpEncoding, qValue: Float): HttpEncodingRange = One(encoding, qValue)
}

final case class HttpEncoding private[http] (value: String) extends jm.headers.HttpEncoding
    with LazyValueBytesRenderable with WithQValue[HttpEncodingRange] {
  def withQValue(qValue: Float): HttpEncodingRange = HttpEncodingRange(this, qValue.toFloat)
}

object HttpEncoding {
  def custom(value: String): HttpEncoding = apply(value)
}

// see http://www.iana.org/assignments/http-parameters/http-parameters.xml
object HttpEncodings extends ObjectRegistry[String, HttpEncoding] {
  // format: OFF
  val compress         = register("compress")
  val chunked          = register("chunked")
  val deflate          = register("deflate")
  val gzip             = register("gzip")
  val identity         = register("identity")
  val `x-compress`     = register("x-compress")
  val `x-zip`          = register("x-zip")
  // format: ON

  private def register(encoding: HttpEncoding): HttpEncoding = register(encoding.value.toRootLowerCase, encoding)
  private def register(value: String): HttpEncoding = register(HttpEncoding(value))
}
