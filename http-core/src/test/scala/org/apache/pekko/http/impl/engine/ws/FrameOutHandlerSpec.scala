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

package org.apache.pekko.http.impl.engine.ws

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, Promise }

import org.apache.pekko
import pekko.http.impl.engine.ws.FrameHandler.{ PeerClosed, UserHandlerCompleted }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.testkit.Utils.assertAllStagesStopped
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.testkit.{ CallingThreadDispatcher, ExplicitlyTriggeredScheduler, PekkoSpec, TestDuration }

class FrameOutHandlerSpec extends PekkoSpec(s"""
  pekko.scheduler.implementation = "${classOf[ExplicitlyTriggeredScheduler].getName}"
  pekko.stream.materializer.dispatcher = "${CallingThreadDispatcher.Id}"
  """) {

  private implicit val materializer: Materializer = SystemMaterializer(system).materializer

  "The client WebSocket frame output" should {
    "keep one close deadline across a normal peer acknowledgement" in assertAllStagesStopped {
      type Input = FrameOutHandler.Input
      val scheduler = system.scheduler.asInstanceOf[ExplicitlyTriggeredScheduler]
      val closeTimeout = 100.millis
      val normalClose = FrameEvent.closeFrame(Protocol.CloseCodes.Regular)
      val outgoingClose = Promise[FrameStart]()
      val peerReply = Promise[Option[(Int, Input)]]()
      val waitingForTransport = Promise[Unit]()
      val transportEof = Promise[Option[(Int, Input)]]()

      val output = Source
        .unfoldAsync[Int, Input](0) {
          case 0 => Future.successful(Some((1, UserHandlerCompleted)))
          case 1 => peerReply.future
          case _ =>
            // Direct fusion means this demand came from FrameOutHandler after it consumed PeerClosed.
            waitingForTransport.success(())
            transportEof.future
        }
        .via(FrameOutHandler.create(serverSide = false, closeTimeout, log))
        .map { frame =>
          outgoingClose.trySuccess(frame)
          frame
        }
        .runWith(Sink.seq)

      try {
        Await.result(outgoingClose.future, 3.seconds.dilated) shouldBe normalClose
        scheduler.timePasses(50.millis)
        output.isCompleted shouldBe false

        peerReply.success(Some((2, PeerClosed(Some(Protocol.CloseCodes.Regular)))))
        Await.result(waitingForTransport.future, 3.seconds.dilated)

        // With a 1 ms shorter timer, completion was already visible when timePasses returned.
        scheduler.timePasses(49.millis)
        output.isCompleted shouldBe false

        scheduler.timePasses(1.milli)
        Await.result(output, 3.seconds.dilated) shouldBe Seq(normalClose)
      } finally {
        peerReply.trySuccess(None)
        transportEof.trySuccess(None)
      }
    }
  }
}
