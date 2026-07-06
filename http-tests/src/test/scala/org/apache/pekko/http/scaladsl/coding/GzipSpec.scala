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
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.{ GZIPInputStream, GZIPOutputStream, ZipException }

import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.model.HttpEncodings
import pekko.stream.SystemMaterializer
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.util.ByteString

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
      def brokenCompress(payload: String) = Coders.Gzip.newCompressor.compress(ByteString(payload, "UTF-8"))
      val ex = the[RuntimeException] thrownBy ourDecode(brokenCompress("abcdefghijkl"))
      ex.ultimateCause.getMessage should equal("Truncated GZIP stream")
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

      inflater.endCalls.get() shouldEqual 1
    }
    "release the inflater when decoding fails on truncation" in {
      val inflater = new TrackingInflater

      val ex = the[RuntimeException] thrownBy decodeWith(inflater, streamEncode(smallTextBytes).dropRight(5))
      ex.ultimateCause.getMessage should equal("Truncated GZIP stream")
      inflater.endCalls.get() shouldEqual 1
    }
    "throw early if header is corrupt" in {
      val cause = (the[RuntimeException] thrownBy ourDecode(ByteString(0, 1, 2, 3, 4))).ultimateCause
      cause should ((be(a[ZipException]) and have).message("Not in GZIP format"))
    }
  }

  private def decodeWith(inflater: TrackingInflater, bytes: ByteString): ByteString =
    decoderWith(inflater).decode(bytes)(SystemMaterializer(system).materializer).awaitResult(3.seconds.dilated)

  private def decoderWith(inflater: TrackingInflater): StreamDecoder =
    new StreamDecoder {
      override val encoding = HttpEncodings.gzip

      override def newDecompressorStage(maxBytesPerChunk: Int) =
        () =>
          new GzipDecompressor(maxBytesPerChunk) {
            override protected[coding] def createInflater() = inflater
          }
    }

  private class TrackingInflater extends java.util.zip.Inflater(true) {
    val endCalls = new AtomicInteger

    override def end(): Unit = {
      endCalls.incrementAndGet()
      super.end()
    }
  }
}
