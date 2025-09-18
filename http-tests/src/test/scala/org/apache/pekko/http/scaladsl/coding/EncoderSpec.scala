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

import scala.annotation.nowarn
import scala.concurrent.duration._

import HttpMethods.POST
import headers._

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.model._
import pekko.testkit._
import pekko.util.ByteString

import org.scalatest.wordspec.AnyWordSpec

class EncoderSpec extends AnyWordSpec with CodecSpecSupport {

  "An Encoder" should {
    "not transform the message if messageFilter returns false" in {
      val request = HttpRequest(POST, entity = HttpEntity(smallText.getBytes("UTF8")))
      DummyEncoder.encodeMessage(request) shouldEqual request
    }
    "correctly transform the HttpMessage if messageFilter returns true" in {
      val request = HttpRequest(POST, entity = HttpEntity(smallText))
      val encoded = DummyEncoder.encodeMessage(request)
      encoded.headers shouldEqual List(`Content-Encoding`(DummyEncoder.encoding))
      encoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated) shouldEqual HttpEntity(
        dummyCompress(smallText))
    }
  }

  def dummyCompress(s: String): String = dummyCompress(ByteString(s, "UTF8")).utf8String
  def dummyCompress(bytes: ByteString): ByteString = DummyCompressor.compressAndFinish(bytes)

  case object DummyEncoder extends Encoder {
    val messageFilter = Encoder.DefaultFilter
    val encoding = HttpEncodings.compress
    def newCompressor = DummyCompressor
  }

  @nowarn("msg=is internal API")
  case object DummyCompressor extends Compressor {
    def compress(input: ByteString) = input ++ ByteString("compressed")
    def flush() = ByteString.empty
    def finish() = ByteString.empty

    def compressAndFlush(input: ByteString): ByteString = compress(input)
    def compressAndFinish(input: ByteString): ByteString = compress(input)
  }
}
