/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/**
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model.http2

import org.parboiled2.Base64Parsing
import org.parboiled2.util.Base64
import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.http2.framing.Http2FrameParsing
import pekko.http.impl.engine.http2.FrameEvent.Setting
import pekko.stream.impl.io
import pekko.util.ByteString

import scala.collection.immutable
import scala.util.Try

/**
 * Internal API
 */
@InternalApi
private[pekko] object Http2SettingsHeader {
  val name: String = "http2-settings"

  private val base64url = new Base64("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_=")

  /** as described in RFC4648 5. - https://tools.ietf.org/html/rfc4648#section-5 */
  private val base64UrlStringDecoder: Base64Parsing.Decoder = Base64Parsing.decodeString(base64url)

  def headerValueToBinary(value: String): ByteString =
    ByteString(base64UrlStringDecoder(value.toCharArray))

  def parse(value: String, log: LoggingAdapter): Try[immutable.Seq[Setting]] = Try {
    // settings are a base64url encoded Http2 settings frame
    // https://httpwg.org/specs/rfc7540.html#rfc.section.3.2.1
    val bytes = headerValueToBinary(value)
    val reader = new io.ByteStringParser.ByteReader(bytes)
    Http2FrameParsing.readSettings(reader, log)
  }
}
