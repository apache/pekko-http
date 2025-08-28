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
@deprecated("Actual implementation of Gzip is internal, use Coders.Gzip instead", since = "Akka HTTP 10.2.0")
class Gzip private[http] (compressionLevel: Int, val messageFilter: HttpMessage => Boolean) extends Coder
    with StreamDecoder {
  def this(messageFilter: HttpMessage => Boolean) = {
    this(GzipCompressor.DefaultCompressionLevel, messageFilter)
  }

  val encoding = HttpEncodings.gzip
  private[http] def newCompressor = new GzipCompressor(compressionLevel)
  def newDecompressorStage(maxBytesPerChunk: Int) = () => new GzipDecompressor(maxBytesPerChunk)

  @deprecated("Use Coders.Gzip(compressionLevel = ...) instead", since = "Akka HTTP 10.2.0")
  def withLevel(level: Int): Gzip = new Gzip(level, messageFilter)
}

/**
 * An encoder and decoder for the HTTP 'gzip' encoding.
 */
@InternalApi
@deprecated("Actual implementation of Gzip is internal API, use Coders.Gzip instead", since = "Akka HTTP 10.2.0")
object Gzip extends Gzip(Encoder.DefaultFilter) {
  def apply(messageFilter: HttpMessage => Boolean) = new Gzip(messageFilter)
}
