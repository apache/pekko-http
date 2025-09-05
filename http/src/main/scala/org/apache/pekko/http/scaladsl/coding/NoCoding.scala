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
import pekko.http.impl.util.StreamUtils
import pekko.stream.FlowShape
import pekko.stream.stage.GraphStage
import pekko.util.ByteString
import headers.HttpEncodings

/**
 * An encoder and decoder for the HTTP 'identity' encoding.
 */
@InternalApi
@deprecated("Actual implementation of NoCoding is internal API, use Coders.NoCoding instead",
  since = "Akka HTTP 10.2.0")
object NoCoding extends Coder with StreamDecoder {
  val encoding = HttpEncodings.identity

  override def encodeData[T](t: T)(implicit mapper: DataMapper[T]): T = t
  override def decodeData[T](t: T)(implicit mapper: DataMapper[T]): T = t

  val messageFilter: HttpMessage => Boolean = _ => false

  private[http] def newCompressor = NoCodingCompressor

  def newDecompressorStage(maxBytesPerChunk: Int): () => GraphStage[FlowShape[ByteString, ByteString]] =
    () => StreamUtils.limitByteChunksStage(maxBytesPerChunk)
}

/** Internal API */
@InternalApi
private[coding] object NoCodingCompressor extends Compressor {
  def compress(input: ByteString): ByteString = input
  def flush() = ByteString.empty
  def finish() = ByteString.empty

  def compressAndFlush(input: ByteString): ByteString = input
  def compressAndFinish(input: ByteString): ByteString = input
}
