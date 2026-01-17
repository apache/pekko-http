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

package org.apache.pekko.http.impl.engine

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, DurationInt }

import org.openjdk.jmh.annotations.{
  Benchmark, BenchmarkMode, Mode, OperationsPerInvocation, OutputTimeUnit, Scope, State, TearDown
}

import org.apache.pekko
import pekko.{ BenchTestSourceSameElement, NotUsed }
import pekko.actor.ActorSystem
import pekko.http.impl.engine.ws.FrameEvent
import pekko.http.impl.engine.ws.Protocol.Opcode
import pekko.stream.scaladsl.{ Flow, Keep, Sink, Source }
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

object MessageToFrameRendererBenchmark {
  final val OperationsPerInvocation = 100000
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class MessageToFrameRendererBenchmark {
  import MessageToFrameRendererBenchmark.OperationsPerInvocation

  private val config = ConfigFactory.parseString("""
    akka.actor.default-dispatcher {
      executor = "fork-join-executor"
      fork-join-executor {
        parallelism-factor = 1
      }
    }
    """)

  private implicit val system: ActorSystem = ActorSystem("MessageToFrameRendererBenchmark", config)

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  def createSource(count: Int): Source[ByteString, NotUsed] =
    Source.fromGraph(new BenchTestSourceSameElement(count, ByteString.empty))

  private val newstreamedFrames = createSource(OperationsPerInvocation)
    .statefulMap(() => true)((isFirst, data) => {
        val frameOpcode = if (isFirst) Opcode.Pong else Opcode.Continuation
        (false, FrameEvent.fullFrame(frameOpcode, None, data, fin = false))
      }, _ => None)
    .toMat(Sink.ignore)(Keep.right)

  private val oldstreamedFrames = createSource(OperationsPerInvocation)
    .via(statefulMap(() => {
      var isFirst = true

      { data =>
        val frameOpcode =
          if (isFirst) {
            isFirst = false
            Opcode.Pong
          } else Opcode.Continuation

        FrameEvent.fullFrame(frameOpcode, None, data, fin = false)
      }
    }))
    .toMat(Sink.ignore)(Keep.right)

  def statefulMap[T, U](functionConstructor: () => T => U): Flow[T, U, NotUsed] =
    Flow[T].statefulMapConcat { () =>
      val f = functionConstructor()
      i => f(i) :: Nil
    }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchOldStreamedFrames(): Unit =
    Await.result(oldstreamedFrames.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchNewStreamedFrames(): Unit =
    Await.result(newstreamedFrames.run(), Duration.Inf)

}
