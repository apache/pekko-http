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

package org.apache.pekko.http.impl.engine.http2

import java.util.concurrent.{ ConcurrentLinkedQueue, ThreadFactory }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.Cancellable
import pekko.event.LoggingAdapter
import FrameEvent.{ GoAwayFrame, PingFrame }
import Http2Protocol.{ ErrorCode, FrameType }
import pekko.http.scaladsl.model.{ HttpProtocols, HttpRequest, HttpResponse }
import pekko.http.scaladsl.settings.ServerSettings
import pekko.testkit.ExplicitlyTriggeredScheduler
import pekko.util.ByteString

object Http2PingSchedulingSpec {
  // Delay execution, not the clock: fixed-rate tasks accumulate while fixed-delay
  // tasks only schedule their next execution after the delayed task has run.
  // This helper models delayed execution, not cancellation of already queued runnables.
  final class PausedScheduler(config: Config, log: LoggingAdapter, threadFactory: ThreadFactory)
      extends ExplicitlyTriggeredScheduler(config, log, threadFactory) {
    private val pending = new ConcurrentLinkedQueue[Runnable]()
    private val singleDelays = new ConcurrentLinkedQueue[FiniteDuration]()

    def sawSingleDelay(delay: FiniteDuration): Boolean = singleDelays.contains(delay)

    def countSingleDelay(delay: FiniteDuration): Int = singleDelays.toArray().count(_ == delay)

    override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)(
        implicit executor: ExecutionContext): Cancellable =
      super.schedule(initialDelay, interval, () => { pending.add(runnable); () })

    override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(
        implicit executor: ExecutionContext): Cancellable = {
      val scheduled = super.scheduleOnce(delay, () => { pending.add(runnable); () })
      singleDelays.add(delay)
      scheduled
    }

    def resumePending(): Unit = {
      var next = pending.poll()
      while (next ne null) {
        next.run()
        next = pending.poll()
      }
    }
  }
}

class Http2PingSchedulingSpec extends Http2SpecWithMaterializer("""
    pekko.scheduler.implementation = "org.apache.pekko.http.impl.engine.http2.Http2PingSchedulingSpec$PausedScheduler"
  """) {
  "HTTP/2 keepalive" should {
    "measure an outstanding ping from its send time despite a burst of callbacks" in {
      var now = 0L
      val state = new ConfigurablePing.EnabledPingState(1.second, 2L, () => now)
      state.onTick()
      state.shouldEmitPing() shouldBe false
      state.onTick()
      state.shouldEmitPing() shouldBe true
      state.sendingPing()
      for (_ <- 1 to 10) state.onTick()
      state.pingAckOverdue() shouldBe false
      state.shouldEmitPing() shouldBe false
      now = 1.second.toNanos
      state.pingAckOverdue() shouldBe false
      now += 1L
      state.pingAckOverdue() shouldBe true
      state.onPingAck()
      state.pingAckOverdue() shouldBe false
      state.onDataFrameSeen()
      state.shouldEmitPing() shouldBe false
    }

    "retain the acknowledgment deadline across ordinary data and monotonic clock wraparound" in {
      var now = Long.MaxValue - 500.millis.toNanos
      val state = new ConfigurablePing.EnabledPingState(1.second, 2L, () => now)
      state.sendingPing()
      state.onDataFrameSeen()
      now += 900.millis.toNanos
      state.onTick()
      state.pingAckOverdue() shouldBe false
      now += 200.millis.toNanos
      state.onTick()
      state.pingAckOverdue() shouldBe true
    }

    "allow a ping acknowledgment after delayed timer execution instead of catching up missed ticks".inAssertAllStagesStopped(
      new TestSetup with RequestResponseProbes {
        override def settings: ServerSettings = {
          val defaults = super.settings
          defaults.withHttp2Settings(defaults.http2Settings.withPingInterval(2.seconds).withPingTimeout(1.second))
        }

        network.sendRequest(1, HttpRequest(protocol = HttpProtocols.`HTTP/2.0`))
        user.expectRequest()

        val scheduler = system.scheduler.asInstanceOf[Http2PingSchedulingSpec.PausedScheduler]
        try {
          scheduler.timePasses(1.second)
          scheduler.resumePending()
          scheduler.timePasses(10.seconds)
          scheduler.resumePending()
          network.expectFrame(FrameType.PING, ByteFlag.Zero, 0, ConfigurablePing.Ping.data)
          network.sendFrame(PingFrame(ack = true, data = ConfigurablePing.Ping.data))

          val request = HttpRequest(uri = "/after-pause", protocol = HttpProtocols.`HTTP/2.0`)
          network.sendRequest(3, request)
          user.expectRequest() shouldBe request
          user.emitResponse(3, HttpResponse())
          network.expectDecodedHEADERS(3)
        } finally scheduler.resumePending()
      })

    "time out an unacknowledged ping after delayed timer execution".inAssertAllStagesStopped(
      new TestSetup with RequestResponseProbes {
        override def settings: ServerSettings = {
          val defaults = super.settings
          defaults.withHttp2Settings(defaults.http2Settings.withPingInterval(2.seconds).withPingTimeout(1.second))
        }

        network.sendRequest(1, HttpRequest(protocol = HttpProtocols.`HTTP/2.0`))
        user.expectRequest()

        val scheduler = system.scheduler.asInstanceOf[Http2PingSchedulingSpec.PausedScheduler]
        try {
          scheduler.timePasses(1.second)
          scheduler.resumePending()
          scheduler.timePasses(10.seconds)
          scheduler.resumePending()
          network.expectFrame(FrameType.PING, ByteFlag.Zero, 0, ConfigurablePing.Ping.data)

          // The scheduler stays paused while real time advances beyond the ping deadline.
          // Advancing scheduler time alone must not stand in for elapsed monotonic time.
          network.expectNoBytes(1200.millis)
          scheduler.timePasses(1.second)
          scheduler.resumePending()
          val goAway = network.expect[GoAwayFrame]()
          goAway.lastStreamId shouldBe 1
          goAway.errorCode shouldBe ErrorCode.PROTOCOL_ERROR
          goAway.debug.utf8String shouldBe "Ping ack timeout"
          network.toNet.cancel()
        } finally scheduler.resumePending()
      })

  }

  "HTTP/2 graceful shutdown advisory" should {
    def scheduler: Http2PingSchedulingSpec.PausedScheduler =
      system.scheduler.asInstanceOf[Http2PingSchedulingSpec.PausedScheduler]

    abstract class AdvisorySetup extends TestSetup with RequestResponseProbes {
      def advisoryTimeout: FiniteDuration = 2.seconds

      override def settings: ServerSettings =
        ServerSettings(s"pekko.http.server.http2.graceful-shutdown-advisory-timeout = ${advisoryTimeout.toMillis}ms")
          .withServerHeader(None)

      def expectAdvisory(): PingFrame = {
        network.expectGOAWAY(Int.MaxValue)._2 shouldBe ErrorCode.NO_ERROR
        val ping = network.expect[PingFrame]()
        ping.ack shouldBe false
        ping.data should not be ConfigurablePing.Ping.data
        ping
      }

      def request(path: String): HttpRequest = HttpRequest(uri = path, protocol = HttpProtocols.`HTTP/2.0`)
    }

    "let a request already in flight reach the application before fixing the final boundary".inAssertAllStagesStopped(
      new AdvisorySetup {
        network.sendRequest(1, request("/before"))
        user.expectRequest().uri.path.toString shouldBe "/before"
        val terminated = serverTerminator.terminate(1.minute)
        val ping = expectAdvisory()

        // The second request models HEADERS already sent before the client received the advisory.
        network.sendRequest(3, request("/in-flight"))
        user.expectRequest().uri.path.toString shouldBe "/in-flight"
        user.emitResponse(1, HttpResponse())
        network.expectDecodedResponseHEADERSPairs(1, endStream = true) should contain(":status" -> "200")

        network.sendFrame(ping.copy(ack = true))
        network.expectGOAWAY(3)._2 shouldBe ErrorCode.NO_ERROR
        terminated.isCompleted shouldBe false
        user.emitResponse(3, HttpResponse(entity = "finished"))
        network.expectDecodedHEADERS(3, endStream = false)
        network.expectDATA(3, endStream = true, ByteString("finished"))
        network.expectComplete()
        terminated.futureValue
      })

    "leave an idle connection open until the shutdown PING is acknowledged, using only one advisory timer"
      .inAssertAllStagesStopped(new AdvisorySetup {
        override def settings: ServerSettings = {
          val defaults = ServerSettings(system).withServerHeader(None)
          defaults.withHttp2Settings(defaults.http2Settings.withGracefulShutdownAdvisoryTimeout(advisoryTimeout))
        }

        val timersBefore = scheduler.countSingleDelay(2.seconds)
        val terminated = serverTerminator.terminate(1.minute)
        val ping = expectAdvisory()
        scheduler.countSingleDelay(2.seconds) shouldBe timersBefore + 1
        (serverTerminator.terminate(30.seconds) should be).theSameInstanceAs(terminated)
        (serverTerminator.terminate(2.minutes) should be).theSameInstanceAs(terminated)
        terminated.isCompleted shouldBe false

        network.sendFrame(ping.copy(ack = true))
        network.expectGOAWAY(0)._2 shouldBe ErrorCode.NO_ERROR
        network.expectComplete()
        terminated.futureValue
      })

    "finish the advisory on timeout and ignore a late shutdown acknowledgment".inAssertAllStagesStopped(
      new AdvisorySetup {
        network.sendRequest(1, request("/stream"))
        user.expectRequest()
        val terminated = serverTerminator.terminate(1.minute)
        val ping = expectAdvisory()

        try {
          scheduler.timePasses(2.seconds)
          scheduler.resumePending()
          network.expectGOAWAY(1)._2 shouldBe ErrorCode.NO_ERROR

          // The timed-out connection can still receive a request that was in flight before the advisory.
          network.sendRequest(3, request("/arrived-after-the-boundary"))
          network.expectRST_STREAM(3, ErrorCode.REFUSED_STREAM)

          network.sendFrame(ping.copy(ack = true))
          // Fence the late ACK with a peer PING so the response is sent only after the ACK was processed.
          val peerPing = PingFrame(ack = false, ByteString("late-ack"))
          network.sendFrame(peerPing)
          network.expect[PingFrame]() shouldBe peerPing.copy(ack = true)

          user.emitResponse(1, HttpResponse())
          network.expectDecodedResponseHEADERSPairs(1, endStream = true) should contain(":status" -> "200")
          network.expectComplete()
          terminated.futureValue
        } finally scheduler.resumePending()
      })

    "keep the advisory open when an outstanding keepalive ping is acknowledged".inAssertAllStagesStopped(
      new AdvisorySetup {
        override def settings: ServerSettings = {
          val defaults = super.settings
          defaults.withHttp2Settings(defaults.http2Settings.withPingInterval(1.second))
        }

        network.sendRequest(1, request("/before-keepalive"))
        user.expectRequest()
        try {
          scheduler.timePasses(1.second)
          scheduler.resumePending()
          val keepalive = network.expect[PingFrame]()
          keepalive shouldBe ConfigurablePing.Ping

          val terminated = serverTerminator.terminate(1.minute)
          val advisory = expectAdvisory()
          network.sendFrame(keepalive.copy(ack = true))
          network.sendRequest(3, request("/already-in-flight"))
          user.expectRequest().uri.path.toString shouldBe "/already-in-flight"

          network.sendFrame(advisory.copy(ack = true))
          network.expectGOAWAY(3)._2 shouldBe ErrorCode.NO_ERROR
          user.emitResponse(1, HttpResponse())
          network.expectDecodedHEADERS(1, endStream = true)
          user.emitResponse(3, HttpResponse())
          network.expectDecodedHEADERS(3, endStream = true)
          network.expectComplete()
          terminated.futureValue
        } finally scheduler.resumePending()
      })

    "send the final boundary immediately if the initial hard deadline is no larger than the advisory"
      .inAssertAllStagesStopped(new AdvisorySetup {
        network.sendRequest(1, request("/finish-before-deadline"))
        user.expectRequest()
        val terminated = serverTerminator.terminate(2.seconds)
        network.expectGOAWAY(1)._2 shouldBe ErrorCode.NO_ERROR
        user.emitResponse(1, HttpResponse())
        network.expectDecodedResponseHEADERSPairs(1, endStream = true) should contain((":status", "200"))
        network.expectComplete()
        terminated.futureValue
      })

    "send the final boundary before a later shorter hard deadline ends an age-triggered shutdown"
      .inAssertAllStagesStopped(
        new AdvisorySetup {
          // Only advance the scheduler's virtual clock; leave ample real time for the new deadline callback.
          override def advisoryTimeout: FiniteDuration = 30.seconds

          override def settings: ServerSettings = {
            val defaults = super.settings
            defaults.withHttp2Settings(defaults.http2Settings.withMaxConnectionAge(537.millis)
              .withMaxConnectionAgeJitter(0)
              .withMaxConnectionAgeGrace(Duration.Inf))
          }

          network.sendRequest(1, request("/still-in-flight"))
          user.expectRequest()
          try {
            scheduler.timePasses(537.millis)
            scheduler.resumePending()
            expectAdvisory()

            val shorterDeadline = 123.millis
            val terminated = serverTerminator.terminate(shorterDeadline)
            network.expectGOAWAY(1)._2 shouldBe ErrorCode.NO_ERROR
            terminated.isCompleted shouldBe false
            // Wait for the graph-stage callback to register the deadline before advancing virtual time.
            awaitAssert { scheduler.sawSingleDelay(shorterDeadline) shouldBe true }
            scheduler.timePasses(shorterDeadline)
            scheduler.resumePending()
            network.expectComplete()
            terminated.futureValue
          } finally scheduler.resumePending()
        })

    "send only a single final GOAWAY by default".inAssertAllStagesStopped(new TestSetup with RequestResponseProbes {
      val terminated = serverTerminator.terminate(1.minute)
      network.expectGOAWAY(0)._2 shouldBe ErrorCode.NO_ERROR
      network.expectComplete()
      terminated.futureValue
    })
  }
}
