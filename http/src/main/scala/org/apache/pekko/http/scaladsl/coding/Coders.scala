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
import pekko.http.impl.model.parser.CharacterClasses
import pekko.http.scaladsl.model.{ HttpHeader, HttpMessage }
import pekko.http.scaladsl.model.headers.{ `Content-Encoding`, HttpEncoding }
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

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

  /**
   * Creates an explicitly selected coder from reusable stream flows. Every materialization of the supplied graphs
   * must own independent codec state; their materialized values are discarded.
   * The encoding must be an HTTP token other than the reserved names `identity` and `*`. The decoder
   * decodes messages with exactly one matching Content-Encoding value, matching case-insensitively.
   * It leaves messages with other, repeated, or stacked encodings unchanged.
   *
   * @param decoderFlowForMaxBytesPerChunk creates a decoding flow on each materialization for the requested maximum
   *                                      output chunk size.
   *                                      The decoder implementation is responsible for respecting that size.
   *                                      This does not limit decoder allocations or total bytes. The server
   *                                      `decodeRequestWith` directive separately applies the routing setting
   *                                      `pekko.http.routing.decode-max-size` to the full decoded entity.
   * @param messageFilter                 whether to encode a message; messages with a Content-Encoding header
   *                                      are never encoded again.
   * @since 2.1.0
   */
  def fromFlows(
      encoding: HttpEncoding,
      encoderFlow: Flow[ByteString, ByteString, ?],
      decoderFlowForMaxBytesPerChunk: Int => Flow[ByteString, ByteString, ?],
      messageFilter: HttpMessage => Boolean = Encoder.DefaultFilter): Coder = {
    require(encoding.value.nonEmpty && encoding.value.forall(c => CharacterClasses.tchar(c)),
      "A flow-based coder requires a valid content-coding token")
    require(encoding.value != "*" && !encoding.value.equalsIgnoreCase("identity"),
      "A flow-based coder cannot use a reserved content-coding name")
    new FlowCoder(encoding, messageFilter, encoderFlow, decoderFlowForMaxBytesPerChunk, Decoder.MaxBytesPerChunkDefault)
  }

  val DefaultCoders: Seq[Coder] = Seq(Gzip, Deflate, NoCoding)

  /**
   * Decodes the message with the first of `decoders` whose encoding matches the message's `Content-Encoding`
   * header, for example to decode a response on the client side without checking its encoding first.
   *
   * The message is returned unchanged if it has no `Content-Encoding` header, if none of `decoders` matches its
   * encoding, or if more than one field or coding is present. In those cases the headers and entity are preserved.
   * Only flow-based coders also recognize a single application-supplied raw `Content-Encoding` header.
   *
   * @param decoders the decoders to choose from, [[Zstd]] is not one of the [[DefaultCoders]]
   * @since 2.0.0
   */
  def decodeMessage(message: HttpMessage, decoders: Seq[Decoder] = DefaultCoders): message.Self =
    singleContentEncodingHeader(message) match {
      case Some(`Content-Encoding`(Seq(encoding))) =>
        decoders.find(_.encoding.value.equalsIgnoreCase(encoding.value)) match {
          case Some(decoder) => decoder.decodeMessage(message)
          case None          => message.self
        }
      case Some(_: `Content-Encoding`) => message.self
      case Some(_)                     =>
        decoders.collectFirst {
          case flow: FlowCoder if flow.matchesSingleContentEncoding(message) => flow.decodeMessage(message)
        }.getOrElse(message.self)
      case None => message.self
    }

  private[coding] def singleContentEncodingHeader(message: HttpMessage): Option[HttpHeader] = {
    val headers = message.headers.iterator
    val first = headers.find(Encoder.isContentEncodingHeader)
    if (first.isDefined && headers.exists(Encoder.isContentEncodingHeader)) None else first
  }

}
