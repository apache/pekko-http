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
import pekko.util.ByteString
import java.io.{ InputStream, OutputStream }
import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip._

import pekko.http.scaladsl.model.HttpMethods.POST
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest }
import pekko.http.impl.util._
import pekko.http.scaladsl.model.headers.{ `Content-Encoding`, HttpEncodings }
import pekko.stream.SystemMaterializer
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit._
import scala.annotation.nowarn

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class DeflateSpec extends CoderSpec {
  protected def Coder: Coder = Coders.Deflate

  protected def newDecodedInputStream(underlying: InputStream): InputStream =
    new InflaterInputStream(underlying)

  protected def newEncodedOutputStream(underlying: OutputStream): OutputStream =
    new DeflaterOutputStream(underlying)

  override def extraTests(): Unit = {
    "throw early if header is corrupt" in {
      (the[RuntimeException] thrownBy {
        ourDecode(ByteString(0, 1, 2, 3, 4))
      }).ultimateCause should be(a[DataFormatException])
    }
    "properly round-trip encode/decode an HttpRequest using no-wrap and best compression" in {
      val request = HttpRequest(POST, entity = HttpEntity(largeText))
      Coders.Deflate.decodeMessage(encodeMessage(request, Deflater.BEST_COMPRESSION, noWrap = true)).toStrict(
        3.seconds.dilated)
        .awaitResult(3.seconds.dilated) should equal(request)
    }
    "properly round-trip encode/decode an HttpRequest using no-wrap and no compression" in {
      val request = HttpRequest(POST, entity = HttpEntity(largeText))
      Coders.Deflate.decodeMessage(encodeMessage(request, Deflater.NO_COMPRESSION, noWrap = true)).toStrict(
        3.seconds.dilated)
        .awaitResult(3.seconds.dilated) should equal(request)
    }
    "properly round-trip encode/decode an HttpRequest with wrapping and no compression" in {
      val request = HttpRequest(POST, entity = HttpEntity(largeText))
      Coders.Deflate.decodeMessage(encodeMessage(request, Deflater.NO_COMPRESSION, noWrap = false)).toStrict(
        3.seconds.dilated)
        .awaitResult(3.seconds.dilated) should equal(request)
    }
    "release the inflater when decoding completes" in {
      val inflater = new TrackingInflater
      decodeWith(inflater, streamEncode(smallTextBytes)) should readAs(smallText)
      inflater.endCalls.get() shouldEqual 1
    }
    "release the inflater when decoding is cancelled early" in {
      val inflater = new TrackingInflater
      val compressed = streamEncode(largeTextBytes)

      Source.single(compressed)
        .via(decoderWith(inflater).withMaxBytesPerChunk(1).decoderFlow)
        .take(1)
        .runWith(Sink.ignore)
        .awaitResult(3.seconds.dilated)

      // postStop() (which calls end()) is dispatched to the stage actor after the
      // Sink.ignore future completes, so we must wait for end() itself rather than
      // for the stream future to avoid a race.
      inflater.awaitEnd(3.seconds.dilated)
      inflater.endCalls.get() shouldEqual 1
    }
    "release the inflater when decoding is truncated" in {
      val inflater = new TrackingInflater
      // Truncated deflate streams complete without exception (completeStage on truncation)
      decodeWith(inflater, streamEncode(smallTextBytes).dropRight(5))
      inflater.endCalls.get() shouldEqual 1
    }
    "release the deflater when encoding completes" in {
      val tracking = new TrackingDeflater
      Source.single(smallTextBytes)
        .via(encoderWith(tracking).encoderFlow)
        .runWith(Sink.ignore)
        .awaitResult(3.seconds.dilated)
      tracking.awaitEnd(3.seconds.dilated)
      tracking.endCalls.get() shouldEqual 1
    }
    "release the deflater when encoding is cancelled early" in {
      val tracking = new TrackingDeflater
      Source.single(largeTextBytes)
        .via(encoderWith(tracking).encoderFlow)
        .take(1)
        .runWith(Sink.ignore)
        .awaitResult(3.seconds.dilated)
      // postStop() (which calls end()) is dispatched to the stage actor after the
      // Sink.ignore future completes, so we must wait for end() itself rather than
      // for the stream future to avoid a race.
      tracking.awaitEnd(3.seconds.dilated)
      tracking.endCalls.get() shouldEqual 1
    }
  }

  private def decodeWith(inflater: TrackingInflater, bytes: ByteString): ByteString =
    decoderWith(inflater).decode(bytes)(SystemMaterializer(system).materializer).awaitResult(3.seconds.dilated)

  @nowarn("msg=deprecated")
  private def encoderWith(tracking: TrackingDeflater): Deflate =
    new Deflate(Encoder.DefaultFilter) {
      override private[http] def newCompressor: DeflateCompressor = new DeflateCompressor() {
        override protected lazy val deflater: java.util.zip.Deflater = tracking
      }
    }

  @nowarn("msg=deprecated")
  private def decoderWith(inflater: TrackingInflater): StreamDecoder =
    new StreamDecoder {
      override val encoding = HttpEncodings.deflate

      override def newDecompressorStage(maxBytesPerChunk: Int) =
        () =>
          new DeflateDecompressor(maxBytesPerChunk) {
            override protected[coding] def createInflater(noWrap: Boolean) = inflater
          }
    }

  private class TrackingInflater extends java.util.zip.Inflater(false) {
    val endCalls = new AtomicInteger
    private val endLatch = new CountDownLatch(1)

    def awaitEnd(atMost: FiniteDuration): Unit =
      endLatch.await(atMost.toMillis, TimeUnit.MILLISECONDS)

    override def end(): Unit = {
      endCalls.incrementAndGet()
      endLatch.countDown()
      super.end()
    }
  }

  private class TrackingDeflater extends java.util.zip.Deflater(Deflater.DEFAULT_COMPRESSION, false) {
    val endCalls = new AtomicInteger
    private val endLatch = new CountDownLatch(1)

    def awaitEnd(atMost: FiniteDuration): Unit =
      endLatch.await(atMost.toMillis, TimeUnit.MILLISECONDS)

    override def end(): Unit = {
      endCalls.incrementAndGet()
      endLatch.countDown()
      super.end()
    }
  }

  private def encodeMessage(request: HttpRequest, compressionLevel: Int, noWrap: Boolean): HttpRequest = {
    @nowarn("msg=deprecated .* is internal API")
    val deflaterWithoutWrapping = new Deflate(Encoder.DefaultFilter) {
      override def newCompressor = new DeflateCompressor(compressionLevel) {
        override lazy val deflater = new Deflater(compressionLevel, noWrap)
      }
    }
    request.transformEntityDataBytes(deflaterWithoutWrapping.encoderFlow)
      .withHeaders(`Content-Encoding`(HttpEncodings.deflate) +: request.headers)
  }
}
