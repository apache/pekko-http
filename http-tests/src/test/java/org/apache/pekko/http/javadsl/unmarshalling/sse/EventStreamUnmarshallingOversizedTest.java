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

package org.apache.pekko.http.javadsl.unmarshalling.sse;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.settings.OversizedSseStrategy;
import org.apache.pekko.http.javadsl.settings.ServerSentEventSettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class EventStreamUnmarshallingOversizedTest {

  private static ActorSystem system;

  @BeforeAll
  public static void setup() {
    system = ActorSystem.create();
  }

  @AfterAll
  public static void teardown() throws Exception {
    system.terminate();
    system.getWhenTerminated().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test
  public void testOversizedStrategyEnum() {
    // Test that the Java enum can be used with the settings
    ServerSentEventSettings settings =
        ServerSentEventSettings.create(system)
            .withLineLength(50)
            .withOversizedLineStrategy(OversizedSseStrategy.FailStream);

    assertEquals(50, settings.maxLineSize(), "Should have correct line length");
    assertEquals(
        OversizedSseStrategy.FailStream,
        settings.getOversizedLineStrategyEnum(),
        "Should have FailStream line strategy");

    // Test all strategies can be set
    settings = settings.withOversizedLineStrategy(OversizedSseStrategy.LogAndSkip);
    assertEquals(
        OversizedSseStrategy.LogAndSkip,
        settings.getOversizedLineStrategyEnum(),
        "Should have LogAndSkip line strategy");

    settings = settings.withOversizedLineStrategy(OversizedSseStrategy.Truncate);
    assertEquals(
        OversizedSseStrategy.Truncate,
        settings.getOversizedLineStrategyEnum(),
        "Should have Truncate line strategy");

    settings = settings.withOversizedLineStrategy(OversizedSseStrategy.DeadLetter);
    assertEquals(
        OversizedSseStrategy.DeadLetter,
        settings.getOversizedLineStrategyEnum(),
        "Should have DeadLetter line strategy");
  }

  @Test
  public void testOversizedLineStrategyStringCompatibility() {
    // Test that the string-based API works for line strategies
    ServerSentEventSettings settings =
        ServerSentEventSettings.create(system).withOversizedLineStrategy("log-and-skip");

    assertEquals(
        "log-and-skip",
        settings.getOversizedLineStrategy(),
        "Should have log-and-skip line strategy string");
    assertEquals(
        OversizedSseStrategy.LogAndSkip,
        settings.getOversizedLineStrategyEnum(),
        "Should have LogAndSkip line strategy enum");
  }
}
