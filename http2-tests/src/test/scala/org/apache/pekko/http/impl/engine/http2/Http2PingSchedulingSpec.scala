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
import pekko.http.impl.engine.http2.FrameEvent.PingFrame
import pekko.http.impl.engine.http2.Http2Protocol.FrameType
import pekko.http.scaladsl.model.{ HttpProtocols, HttpRequest, HttpResponse }
import pekko.http.scaladsl.settings.ServerSettings
import pekko.testkit.ExplicitlyTriggeredScheduler

object Http2PingSchedulingSpec {
  // Delay execution, not the clock: fixed-rate tasks accumulate while fixed-delay
  // tasks only schedule their next execution after the delayed task has run.
  // This helper models delayed execution, not cancellation of already queued runnables.
  final class PausedScheduler(config: Config, log: LoggingAdapter, threadFactory: ThreadFactory)
      extends ExplicitlyTriggeredScheduler(config, log, threadFactory) {
    private val pending = new ConcurrentLinkedQueue[Runnable]()

    override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)(
        implicit executor: ExecutionContext): Cancellable =
      super.schedule(initialDelay, interval, () => { pending.add(runnable); () })

    override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(
        implicit executor: ExecutionContext): Cancellable =
      super.scheduleOnce(delay, () => { pending.add(runnable); () })

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
    "allow a ping acknowledgment after delayed timer execution instead of catching up missed ticks".inAssertAllStagesStopped(
      new TestSetup with RequestResponseProbes {
        override def settings: ServerSettings = {
          val defaults = super.settings
          defaults.withHttp2Settings(defaults.http2Settings.withPingInterval(2.seconds).withPingTimeout(1.second))
        }

        network.sendRequest(1, HttpRequest(protocol = HttpProtocols.`HTTP/2.0`))
        user.expectRequest()

        val scheduler = system.scheduler.asInstanceOf[Http2PingSchedulingSpec.PausedScheduler]
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
      })
  }
}
