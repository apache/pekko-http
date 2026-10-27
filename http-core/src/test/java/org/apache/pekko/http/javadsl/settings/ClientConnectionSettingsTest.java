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

import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.io.Inet;
import org.apache.pekko.io.TcpSO;
import org.junit.jupiter.api.Test;

public class ClientConnectionSettingsTest {

  @Test
  public void testCreateWithActorSystem() {
    ActorSystem sys = ActorSystem.create("test");
    ClientConnectionSettings settings = ClientConnectionSettings.create(sys);
  }

  @Test
  public void testWithSocketOptionsAcceptsIterableOfSocketOptionSubtype() {
    // TcpSO factories return concrete subtypes such as Tcp.SO.KeepAlive
    List<? extends Inet.SocketOption> options =
        List.of(TcpSO.keepAlive(true), TcpSO.receiveBufferSize(1024));

    ClientConnectionSettings updated =
        ClientConnectionSettings.create("").withSocketOptions(options);

    List<Inet.SocketOption> actual = new ArrayList<>();
    updated.getSocketOptions().forEach(actual::add);
    assertEquals(options, actual);
  }
}
