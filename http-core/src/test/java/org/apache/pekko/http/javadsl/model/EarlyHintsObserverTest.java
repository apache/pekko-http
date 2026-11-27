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

package org.apache.pekko.http.javadsl.model;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.junit.jupiter.api.Test;

class EarlyHintsObserverTest {
  @Test
  void materializeAndAttachAnObserver() throws Exception {
    ActorSystem system = ActorSystem.create("early-hints-observer-test");
    try {
      Pair<EarlyHintsObserver, CompletionStage<Done>> observation =
          EarlyHintsObserver.createSource(2)
              .toMat(Sink.foreach(hint -> hint.getHeaders().size()), Keep.both())
              .run(system);
      EarlyHintsObserver observer = observation.first();
      HttpRequest request =
          HttpRequest.GET("/").addAttribute(EarlyHintsObserver.getAttributeKey(), observer);
      assertSame(
          observer, request.getAttribute(EarlyHintsObserver.getAttributeKey()).orElseThrow());
      observer.complete();
      observation.second().toCompletableFuture().get(5, TimeUnit.SECONDS);
      observer.complete();
    } finally {
      system.terminate();
    }
  }

  @Test
  void closeAnObserverAfterConsumerCancellation() throws Exception {
    ActorSystem system = ActorSystem.create("early-hints-observer-cancellation-test");
    try {
      Pair<EarlyHintsObserver, CompletionStage<Done>> observation =
          EarlyHintsObserver.createSource(2)
              .watchTermination(Keep.both())
              .to(Sink.cancelled())
              .run(system);
      observation.second().toCompletableFuture().get(5, TimeUnit.SECONDS);
      observation.first().complete();
      observation.first().complete();
    } finally {
      system.terminate();
    }
  }
}
