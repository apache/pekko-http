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
   * `com.github.luben:zstd-jni` dependency on the classpath, version 1.5.7-25 or later.
   *
   * Following RFC 9659, it does not compress with a window larger than 8 MB, and does not decode frames that need
   * a larger window, so that a peer cannot make it allocate more memory than that for one message.
   *
   * @since 2.0.0
   */
  def Zstd: Coder = pekko.http.scaladsl.coding.Zstd.Default

  /**
   * A coder for the 'zstd' encoding with a custom message filter, compression level and decoder window limit,
   * see [[Zstd]].
   *
   * @param compressionLevel must be between `Compression.ZstdMinCompressionLevel` and
   *                         `Compression.ZstdMaxCompressionLevel` of pekko-stream
   * @param windowLogMax     the log2 of the largest window that the decoder accepts, 23 (8 MB, the limit of RFC 9659)
   *                         by default. It must be 0 or between `Compression.ZstdMinWindowLog` and
   *                         `Compression.ZstdMaxWindowLog` of pekko-stream; 0 uses the limit of libzstd (128 MB)
   *                         and also works with versions of zstd-jni older than 1.5.7-25
   * @since 2.0.0
   */
  def Zstd(
      messageFilter: HttpMessage => Boolean = Encoder.DefaultFilter,
      compressionLevel: Int = pekko.http.scaladsl.coding.Zstd.DefaultCompressionLevel,
      windowLogMax: Int = pekko.http.scaladsl.coding.Zstd.DefaultWindowLogMax): Coder =
    new Zstd(compressionLevel, messageFilter, Decoder.MaxBytesPerChunkDefault, windowLogMax)

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
