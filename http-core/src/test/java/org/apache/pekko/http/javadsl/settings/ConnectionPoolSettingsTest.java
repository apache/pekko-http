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

import java.util.List;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.junit.jupiter.api.Test;

public class ConnectionPoolSettingsTest {

  @Test
  public void testCreateWithActorSystem() {
    ActorSystem sys = ActorSystem.create("test");
    ConnectionPoolSettings settings = ConnectionPoolSettings.create(sys);
  }

  @Test
  public void testWithHostOverridesUsesFirstMatchingPattern() {
    ConnectionPoolSettings defaults =
        ConnectionPoolSettings.create("pekko.http.host-connection-pool.max-connections = 4");
    ConnectionPoolSettings settings =
        defaults.withHostOverrides(
            List.of(
                Pair.create("*.example.com", defaults.withMaxConnections(7)),
                Pair.create("api.example.com", defaults.withMaxConnections(9)),
                Pair.create("other.org", defaults.withMaxConnections(11))));

    assertEquals(7, maxConnectionsFor(settings, "api.example.com"));
    assertEquals(11, maxConnectionsFor(settings, "other.org"));
    assertEquals(4, maxConnectionsFor(settings, "unmatched.net"));
  }

  private static int maxConnectionsFor(ConnectionPoolSettings settings, String host) {
    return ((org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings) settings)
        .forHost(host)
        .getMaxConnections();
  }
}
