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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class EventStreamUnmarshallingOversizedTest extends JUnitSuite {

  private static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create();
  }

  @AfterClass
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
        .withOversizedStrategy(OversizedSseStrategy.FailStream);
    
    assertEquals("Should have correct line length", 50, settings.maxLineSize());
    assertEquals("Should have FailStream strategy", 
        OversizedSseStrategy.FailStream, settings.getOversizedStrategyEnum());

    // Test all strategies can be set
    settings = settings.withOversizedStrategy(OversizedSseStrategy.LogAndSkip);
    assertEquals("Should have LogAndSkip strategy", 
        OversizedSseStrategy.LogAndSkip, settings.getOversizedStrategyEnum());

    settings = settings.withOversizedStrategy(OversizedSseStrategy.Truncate);
    assertEquals("Should have Truncate strategy", 
        OversizedSseStrategy.Truncate, settings.getOversizedStrategyEnum());

    settings = settings.withOversizedStrategy(OversizedSseStrategy.DeadLetter);
    assertEquals("Should have DeadLetter strategy", 
        OversizedSseStrategy.DeadLetter, settings.getOversizedStrategyEnum());
  }

  @Test
  public void testOversizedStrategyStringCompatibility() {
    // Test that the string-based API still works
    ServerSentEventSettings settings = 
        ServerSentEventSettings.create(system)
        .withOversizedStrategy("log-and-skip");
    
    assertEquals("Should have log-and-skip strategy string", 
        "log-and-skip", settings.getOversizedStrategy());
    assertEquals("Should have LogAndSkip strategy enum", 
        OversizedSseStrategy.LogAndSkip, settings.getOversizedStrategyEnum());
  }
}
