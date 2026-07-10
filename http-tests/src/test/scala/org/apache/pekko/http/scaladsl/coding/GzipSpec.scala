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

import java.io.{ InputStream, OutputStream }
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.{ GZIPInputStream, GZIPOutputStream, Inflater, ZipException }

import scala.annotation.nowarn
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.util._
import pekko.stream.SystemMaterializer
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit._
import pekko.util.ByteString
import scala.annotation.nowarn

@nowarn("msg=deprecated .* is internal API")
class GzipSpec extends CoderSpec {
  protected def Coder: Coder = Coders.Gzip(compressionLevel = 9)

  protected def newDecodedInputStream(underlying: InputStream): InputStream =
    new GZIPInputStream(underlying)

  protected def newEncodedOutputStream(underlying: OutputStream): OutputStream =
    new GZIPOutputStream(underlying)

  override def extraTests(): Unit = {
    "decode concatenated compressions" in {
      ourDecode(Seq(encode("Hello, "), encode("dear "), encode("User!")).join) should readAs("Hello, dear User!")
    }
    "provide a better compression ratio than the standard Gzip/Gunzip streams" in pendingUntilFixed {
      // for this input, it seems gzip level 5-9 provide almost the same compression, so < is too strict
      // TODO: find better input where level makes a more significant difference
      ourEncode(largeTextBytes).length should be < streamEncode(largeTextBytes).length
    }
    "throw an error on truncated input" in {
      val ex = the[RuntimeException] thrownBy ourDecode(streamEncode(smallTextBytes).dropRight(5))
      ex.ultimateCause.getMessage should equal("Truncated GZIP stream")
    }
    "throw an error if compressed data is just missing the trailer at the end" in {
      def brokenCompress(payload: String) =
        Coders.Gzip.newCompressor.compress(ByteString(payload, StandardCharsets.UTF_8))
      val ex = the[RuntimeException] thrownBy ourDecode(brokenCompress("abcdefghijkl"))
      ex.ultimateCause.getMessage should equal("Truncated GZIP stream")
    }
    "throw early if header is corrupt" in {
      val cause = (the[RuntimeException] thrownBy ourDecode(ByteString(0, 1, 2, 3, 4))).ultimateCause
      cause should ((be(a[ZipException]) and have).message("Not in GZIP format"))
    }
    "release the inflater when decoding completes" in {
      val tracking = new TrackingInflater
      decodeWith(tracking, streamEncode(smallTextBytes)) should readAs(smallText)
      tracking.endCalls.get() shouldEqual 1
    }
    "release the inflater when decoding is cancelled early" in {
      val tracking = new TrackingInflater

      Source.single(streamEncode(largeTextBytes))
        .via(decoderWith(tracking).withMaxBytesPerChunk(1).decoderFlow)
        .take(1)
        .runWith(Sink.ignore)
        .awaitResult(3.seconds.dilated)

      // postStop() (which calls end()) is dispatched to the stage actor after the
      // Sink.ignore future completes, so we must wait for end() itself rather than
      // for the stream future to avoid a race.
      tracking.awaitEnd(3.seconds.dilated)
      tracking.endCalls.get() shouldEqual 1
    }
    "release the inflater when decoding fails on truncation" in {
      val inflater = new TrackingInflater

      val ex = the[RuntimeException] thrownBy decodeWith(inflater, streamEncode(smallTextBytes).dropRight(5))
      ex.ultimateCause.getMessage should equal("Truncated GZIP stream")
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

  private def decodeWith(tracking: TrackingInflater, bytes: ByteString): ByteString =
    decoderWith(tracking).decode(bytes)(SystemMaterializer(system).materializer).awaitResult(3.seconds.dilated)

  @nowarn("msg=deprecated")
  private def decoderWith(tracking: TrackingInflater): Gzip =
    new Gzip(Encoder.DefaultFilter) {
      override def newDecompressorStage(maxBytesPerChunk: Int) =
        () =>
          new GzipDecompressor(maxBytesPerChunk) {
            override protected[coding] def createInflater(): Inflater = tracking
          }
    }

  @nowarn("msg=deprecated")
  private def encoderWith(tracking: TrackingDeflater): Gzip =
    new Gzip(Encoder.DefaultFilter) {
      override private[http] def newCompressor: GzipCompressor = new GzipCompressor() {
        override protected lazy val deflater: java.util.zip.Deflater = tracking
      }
    }

  private class TrackingInflater extends java.util.zip.Inflater(true) {
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

  private class TrackingDeflater extends java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, true) {
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
}
