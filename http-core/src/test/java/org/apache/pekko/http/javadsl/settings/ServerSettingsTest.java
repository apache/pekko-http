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

package org.apache.pekko.http.javadsl.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.apache.pekko.actor.ActorSystem;
import org.junit.jupiter.api.Test;

public class ServerSettingsTest {

  @Test
  public void testCreateWithActorSystem() {
    ActorSystem sys = ActorSystem.create("test");
    ServerSettings settings = ServerSettings.create(sys);
  }

  @Test
  public void testTimeoutsGettersReturnJavaDurations() {
    ServerSettings.Timeouts timeouts =
        ServerSettings.create(
                "pekko.http.server { idle-timeout = 61 s, request-timeout = infinite, "
                    + "bind-timeout = 2 s, linger-timeout = 3 min }")
            .getTimeouts();
    assertEquals(Duration.ofSeconds(61), timeouts.getIdleTimeout());
    assertEquals(ChronoUnit.FOREVER.getDuration(), timeouts.getRequestTimeout());
    assertEquals(Duration.ofSeconds(2), timeouts.getBindTimeout());
    assertEquals(Duration.ofMinutes(3), timeouts.getLingerTimeout());
  }

  @Test
  public void testTimeoutsWithJavaDurations() {
    ServerSettings settings = ServerSettings.create("");
    ServerSettings updated =
        settings.withTimeouts(
            settings
                .getTimeouts()
                .withIdleTimeout(ChronoUnit.FOREVER.getDuration())
                .withRequestTimeout(Duration.ofSeconds(5))
                .withBindTimeout(Duration.ofMillis(500))
                .withLingerTimeout(Duration.ofSeconds(7)));

    ServerSettings.Timeouts timeouts = updated.getTimeouts();
    assertEquals(scala.concurrent.duration.Duration.Inf(), timeouts.idleTimeout());
    assertEquals(ChronoUnit.FOREVER.getDuration(), timeouts.getIdleTimeout());
    assertEquals(Duration.ofSeconds(5), timeouts.getRequestTimeout());
    assertEquals(Duration.ofMillis(500), timeouts.getBindTimeout());
    assertEquals(Duration.ofSeconds(7), timeouts.getLingerTimeout());
  }

  @Test
  public void testStreamCancellationDelayWithJavaDurations() {
    ServerSettings settings =
        ServerSettings.create("pekko.http.server.stream-cancellation-delay = 150 ms");
    assertEquals(Duration.ofMillis(150), settings.getStreamCancellationDelay());

    ServerSettings updated = settings.withStreamCancellationDelay(Duration.ofSeconds(2));
    assertEquals(Duration.ofSeconds(2), updated.getStreamCancellationDelay());
  }
}
