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

import org.apache.pekko
import pekko.http.scaladsl.model.HttpMessage
import pekko.http.scaladsl.model.headers.`Content-Encoding`

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

  /**
   * A coder for the 'zstd' encoding. It is not one of the [[DefaultCoders]], as it needs the optional
   * `com.github.luben:zstd-jni` dependency on the classpath.
   *
   * @since 2.0.0
   */
  def Zstd: Coder = pekko.http.scaladsl.coding.Zstd.Default

  /**
   * A coder for the 'zstd' encoding with a custom message filter and compression level, see [[Zstd]].
   *
   * @param compressionLevel must be between `Compression.ZstdMinCompressionLevel` and
   *                         `Compression.ZstdMaxCompressionLevel` of pekko-stream
   * @since 2.0.0
   */
  def Zstd(
      messageFilter: HttpMessage => Boolean = Encoder.DefaultFilter,
      compressionLevel: Int = pekko.http.scaladsl.coding.Zstd.DefaultCompressionLevel): Coder =
    new Zstd(compressionLevel, messageFilter, Decoder.MaxBytesPerChunkDefault)

  val DefaultCoders: Seq[Coder] = Seq(Gzip, Deflate, NoCoding)

  /**
   * Decodes the message with the one of `decoders` whose encoding matches the message's `Content-Encoding`
   * header, for example to decode a response on the client side without checking its encoding first.
   *
   * The message is returned unchanged if it has no `Content-Encoding` header, if none of `decoders` matches its
   * encoding, or if the header lists more than one encoding. In the last two cases the `Content-Encoding` header
   * is kept, so `message.encoding` shows that the entity is still encoded.
   *
   * @param decoders the decoders to choose from, [[Zstd]] is not one of the [[DefaultCoders]]
   * @since 2.0.0
   */
  def decodeMessage(message: HttpMessage, decoders: Seq[Decoder] = DefaultCoders): message.Self =
    message.header[`Content-Encoding`] match {
      case Some(`Content-Encoding`(Seq(encoding))) =>
        decoders.find(_.encoding == encoding) match {
          case Some(decoder) => decoder.decodeMessage(message)
          case None          => message.self
        }
      case _ => message.self
    }
}
