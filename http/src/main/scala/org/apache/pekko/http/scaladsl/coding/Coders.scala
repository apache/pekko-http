/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.coding

import scala.annotation.nowarn
import scala.collection.immutable

import org.apache.pekko
import pekko.http.scaladsl.model.HttpMessage

@nowarn("msg=in package coding is deprecated")
object Coders {
  def Gzip: Coder = pekko.http.scaladsl.coding.Gzip
  def Gzip(
      messageFilter: HttpMessage => Boolean = Encoder.DefaultFilter,
      compressionLevel: Int = GzipCompressor.DefaultCompressionLevel): Coder =
    new Gzip(compressionLevel, messageFilter)

  def Deflate: Coder = pekko.http.scaladsl.coding.Deflate
  def Deflate(
      messageFilter: HttpMessage => Boolean = Encoder.DefaultFilter,
      compressionLevel: Int = DeflateCompressor.DefaultCompressionLevel): Coder =
    new Deflate(compressionLevel, messageFilter)

  def NoCoding: Coder = pekko.http.scaladsl.coding.NoCoding

  val DefaultCoders: immutable.Seq[Coder] = immutable.Seq(Gzip, Deflate, NoCoding)
}
