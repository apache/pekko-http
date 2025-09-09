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

package org.apache.pekko.http.scaladsl.coding

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.HttpEncodings

@InternalApi
@deprecated("Actual implementation of Deflate is internal API, use Coders.Deflate instead", since = "Akka HTTP 10.2.0")
class Deflate private[http] (compressionLevel: Int, val messageFilter: HttpMessage => Boolean) extends Coder
    with StreamDecoder {
  def this(messageFilter: HttpMessage => Boolean) = {
    this(DeflateCompressor.DefaultCompressionLevel, messageFilter)
  }

  val encoding = HttpEncodings.deflate
  private[http] def newCompressor = new DeflateCompressor(compressionLevel)
  def newDecompressorStage(maxBytesPerChunk: Int) = () => new DeflateDecompressor(maxBytesPerChunk)

  @deprecated("Use Coders.Deflate(compressionLevel = ...) instead", since = "Akka HTTP 10.2.0")
  def withLevel(level: Int): Deflate = new Deflate(level, messageFilter)
}
@InternalApi
@deprecated("Actual implementation of Deflate is internal API, use Coders.Deflate instead", since = "Akka HTTP 10.2.0")
object Deflate extends Deflate(Encoder.DefaultFilter)
