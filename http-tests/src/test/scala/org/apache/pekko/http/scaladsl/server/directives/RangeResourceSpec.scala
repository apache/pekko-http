/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.scaladsl.server
package directives

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.ExecutionContext.parasitic
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.Done
import pekko.http.RangeResponsePolicy
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.StatusCodes._
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.unmarshalling.Unmarshal
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.Utils.assertAllStagesStopped
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit._
import pekko.util.ByteString

class RangeResourceSpec extends RoutingSpec {
  private val waitTime = 3.seconds.dilated
  private val text = ContentTypes.`text/plain(UTF-8)`
  private val sample = ByteString("abcdefghijklmnopqrstuvwxyz")

  private def record(resource: ByteString, reads: ConcurrentLinkedQueue[(Long, Long)], closes: AtomicInteger) =
    RangeResource(text, resource.size) { () =>
      Future.successful(new RangeResource.Reader {
        override def read(offset: Long, length: Long): Source[ByteString, Any] = {
          reads.add(offset -> length)
          Source.single(resource.slice(offset.toInt, (offset + length).toInt))
        }
        override def close(): Unit = { closes.incrementAndGet(); () }
      })
    }

  "A positional resource" should {
    "serve full and single-range Scala routes through one bounded provider read each" in {
      val reads = new ConcurrentLinkedQueue[(Long, Long)]
      val closes = new AtomicInteger
      val resource = record(sample, reads, closes)
      val route = getFromRangeResource(resource)

      Get() ~> route ~> check {
        status shouldEqual OK
        header[`Content-Range`] shouldBe None
        responseAs[String] shouldEqual sample.utf8String
      }
      Get() ~> addHeader(Range(ByteRange(4, 7))) ~> route ~> check {
        status shouldEqual PartialContent
        header[`Content-Range`] shouldEqual Some(`Content-Range`(ContentRange(4, 7, sample.size)))
        responseAs[String] shouldEqual "efgh"
      }

      reads.asScala.toVector shouldEqual Vector(0L -> sample.size.toLong, 4L -> 4L)
      awaitAssert(closes.get() shouldEqual 2)
    }

    "request only sparse retained regions and wait for the active region before opening the next" in {
      val total = 1000000L
      val high = 500000L -> 3L
      val low = 15L -> 2L
      val reads = new ConcurrentLinkedQueue[(Long, Long)]
      val opened = new AtomicInteger
      val closed = new AtomicInteger
      val firstStarted = Promise[Done]()
      val allowFirst = Promise[ByteString]()
      val resource = RangeResource(text, total) { () =>
        opened.incrementAndGet()
        Future.successful(new RangeResource.Reader {
          override def read(offset: Long, length: Long): Source[ByteString, Any] = {
            val region = offset -> length
            reads.add(region)
            if (region == high) {
              firstStarted.trySuccess(Done)
              Source.future(allowFirst.future)
            } else Source.single(ByteString("de"))
          }
          override def close(): Unit = { closed.incrementAndGet(); () }
        })
      }
      val policy = RangeResponsePolicy.create().withPreserveRequestOrder(true)
      val requested = Range(ByteRange(high._1, high._1 + high._2 - 1), ByteRange(low._1, low._1 + low._2 - 1))
      val response = resource.responseFor(HttpMethods.GET, List(requested), policy).get
      response.status shouldEqual PartialContent
      response.header[`Content-Range`] shouldBe None
      opened.get() shouldEqual 0
      val parts = Unmarshal(response.entity).to[Multipart.ByteRanges]
        .flatMap(_.toStrict(waitTime))(system.dispatcher)

      try {
        Await.result(firstStarted.future, waitTime)
        reads.asScala.toVector shouldEqual Vector(high)
        opened.get() shouldEqual 1
        closed.get() shouldEqual 0
      } finally allowFirst.trySuccess(ByteString("abc"))

      val result = Await.result(parts, waitTime)
      result.strictParts.map(_.contentRange) shouldEqual
      List(ContentRange(high._1, high._1 + high._2 - 1, total), ContentRange(low._1, low._1 + low._2 - 1, total))
      result.strictParts.map(_.entity.data) shouldEqual List(ByteString("abc"), ByteString("de"))
      reads.asScala.toVector shouldEqual Vector(high, low)
      reads.asScala.iterator.map(_._2).sum shouldEqual 5L
      awaitAssert(closed.get() shouldEqual 1)
    }

    "keep normal HEAD and not-modified responses free of provider reads" in {
      val opens = new AtomicInteger
      val tag = EntityTag("snapshot")
      val body = RangeResource(text, sample.size) { () =>
        opens.incrementAndGet()
        Future.failed(new IllegalStateException("metadata responses should not acquire a reader"))
      }.withEntityTag(tag)
      val requested = Range(ByteRange(10, 13))
      val route = getFromRangeResource(body)
      val plan = body.responseFor(HttpMethods.HEAD, List(requested)).get
      plan.status shouldEqual OK
      plan.header[`Content-Range`] shouldBe None
      plan.entity.contentLengthOption shouldEqual Some(sample.size.toLong)
      Head() ~> addHeader(requested) ~> route ~> check {
        status shouldEqual OK
        header[`Content-Range`] shouldBe None
        header[ETag] shouldEqual Some(ETag(tag))
        responseEntity.contentLengthOption shouldEqual Some(sample.size.toLong)
      }
      Get() ~> addHeader(`If-None-Match`(tag)) ~> addHeader(requested) ~> route ~> check {
        status shouldEqual NotModified
        header[ETag] shouldEqual Some(ETag(tag))
      }
      opens.get() shouldEqual 0
    }

    "use the default standalone policy and ignore Range when If-Range is unresolved" in {
      val reads = new ConcurrentLinkedQueue[(Long, Long)]
      val closes = new AtomicInteger
      val resource = record(sample, reads, closes)
      val requested = Range(ByteRange(4, 7))
      val validator = `If-Range`(Left(EntityTag("snapshot")))
      val response = resource.responseFor(HttpMethods.GET, List(requested, validator)).get
      response.status shouldEqual OK
      response.header[`Content-Range`] shouldBe None
      reads.isEmpty shouldBe true
      Await.result(response.entity.toStrict(waitTime), waitTime).data shouldEqual sample
      reads.asScala.toVector shouldEqual Vector(0L -> sample.size.toLong)
      awaitAssert(closes.get() shouldEqual 1)
    }

    "report the known complete length when rejecting valid ranges under the configured count limit" in {
      val opened = new AtomicInteger
      val resource = RangeResource(text, sample.size) { () =>
        opened.incrementAndGet()
        Future.failed(new IllegalStateException("a planned rejection should not acquire a reader"))
      }
      val requested = Range(ByteRange(2, 3), ByteRange(18, 20))
      val policy = RangeResponsePolicy.create().withRangeCountLimit(1)
      val response = resource.responseFor(HttpMethods.GET, List(requested), policy).get
      response.status shouldEqual RangeNotSatisfiable
      response.header[`Content-Range`] shouldEqual Some(`Content-Range`(ContentRange.Unsatisfiable(sample.size)))
      response.entity.isKnownEmpty shouldBe true
      opened.get() shouldEqual 0
    }

    "release an asynchronously opened reader when demand is cancelled before its open completes" in {
      val started = Promise[Done]()
      val opened = Promise[RangeResource.Reader]()
      val closed = Promise[Done]()
      val closes = new AtomicInteger
      val reads = new AtomicInteger
      val resource = RangeResource(text, sample.size) { () =>
        started.trySuccess(Done)
        opened.future
      }
      val response = resource.responseFor(HttpMethods.GET, List(Range(ByteRange(4, 7)))).get
      assertAllStagesStopped {
        val out = response.entity.dataBytes.runWith(TestSink[ByteString]())
        try {
          out.request(1)
          Await.result(started.future, waitTime)
        } finally out.cancel()
      }
      opened.success(new RangeResource.Reader {
        override def read(offset: Long, length: Long): Source[ByteString, Any] = {
          reads.incrementAndGet()
          Source.single(ByteString("efgh"))
        }
        override def close(): Unit = {
          closes.incrementAndGet()
          closed.trySuccess(Done)
          ()
        }
      })
      Await.result(closed.future, waitTime)
      reads.get() shouldEqual 0
      closes.get() shouldEqual 1
    }

    "cancel an active region stream and close the owning session once" in {
      val terminated = Promise[Done]()
      val closed = Promise[Done]()
      val closes = new AtomicInteger
      val resource = RangeResource(text, sample.size) { () =>
        Future.successful(new RangeResource.Reader {
          override def read(offset: Long, length: Long): Source[ByteString, Any] =
            Source.single(ByteString("ef")).concatLazy(Source.maybe[ByteString])
              .watchTermination((_, done) => done.onComplete(terminated.tryComplete)(parasitic))
          override def close(): Unit = {
            closes.incrementAndGet()
            closed.trySuccess(Done)
            ()
          }
        })
      }
      val response = resource.responseFor(HttpMethods.GET, List(Range(ByteRange(4, 7)))).get
      assertAllStagesStopped {
        val out = response.entity.dataBytes.runWith(TestSink[ByteString]())
        try {
          out.request(1).expectNext(ByteString("ef"))
        } finally out.cancel()
      }
      Await.result(terminated.future, waitTime)
      Await.result(closed.future, waitTime)
      closes.get() shouldEqual 1
    }
  }
}
