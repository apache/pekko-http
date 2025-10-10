/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.marshallers.sprayjson

import java.nio.charset.StandardCharsets

import spray.json.ParserInput.IndexedBytesParserInput

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString

/**
 * INTERNAL API
 *
 * ParserInput reading directly off a ByteString. (Based on the ByteArrayBasedParserInput)
 * that avoids a separate decoding step.
 */
@InternalApi
private[sprayjson] final class SprayJsonByteStringParserInput(bytes: ByteString) extends IndexedBytesParserInput {
  protected def byteAt(offset: Int): Byte = bytes(offset)

  override def length: Int = bytes.size
  override def sliceString(start: Int, end: Int): String =
    bytes.slice(start, end - start).decodeString(StandardCharsets.UTF_8)
  override def sliceCharArray(start: Int, end: Int): Array[Char] =
    StandardCharsets.UTF_8.decode(bytes.slice(start, end).asByteBuffer).array()
}
