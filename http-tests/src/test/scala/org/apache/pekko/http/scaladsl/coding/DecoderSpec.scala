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
import pekko.stream.{ Attributes, FlowShape }
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.stage._
import pekko.testkit._
import pekko.util.ByteString

import org.scalatest.wordspec.AnyWordSpec

class DecoderSpec extends AnyWordSpec with CodecSpecSupport {

  "A Decoder" should {
    "not transform the message if it doesn't contain a Content-Encoding header" in {
      val request = HttpRequest(POST, entity = HttpEntity(smallText))
      DummyDecoder.decodeMessage(request) shouldEqual request
    }
    "correctly transform the message if it contains a Content-Encoding header" in {
      val request =
        HttpRequest(POST, entity = HttpEntity(smallText), headers = List(`Content-Encoding`(DummyDecoder.encoding)))
      val decoded = DummyDecoder.decodeMessage(request)
      decoded.headers shouldEqual Nil
      decoded.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated) shouldEqual HttpEntity(
        dummyDecompress(smallText))
    }
  }

  def dummyDecompress(s: String): String = dummyDecompress(ByteString(s, "UTF8")).decodeString("UTF8")
  def dummyDecompress(bytes: ByteString): ByteString = DummyDecoder.decode(bytes).awaitResult(3.seconds.dilated)

  @nowarn("msg=is internal API")
  case object DummyDecoder extends StreamDecoder {
    val encoding = HttpEncodings.compress

    override def newDecompressorStage(maxBytesPerChunk: Int): () => GraphStage[FlowShape[ByteString, ByteString]] =
      () =>
        new SimpleLinearGraphStage[ByteString] {
          override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
            setHandler(in,
              new InHandler {
                override def onPush(): Unit = push(out, grab(in) ++ ByteString("compressed"))
              })
            setHandler(out,
              new OutHandler {
                override def onPull(): Unit = pull(in)
              })
          }
        }
  }

}
