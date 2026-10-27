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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.io.Inet;
import org.apache.pekko.io.TcpSO;
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

  @Test
  public void testWithSocketOptionsAcceptsIterableOfSocketOptionSubtype() {
    // TcpSO factories return concrete subtypes such as Tcp.SO.KeepAlive
    List<? extends Inet.SocketOption> options =
        List.of(TcpSO.keepAlive(true), TcpSO.receiveBufferSize(1024));

    ServerSettings updated = ServerSettings.create("").withSocketOptions(options);

    List<Inet.SocketOption> actual = new ArrayList<>();
    updated.getSocketOptions().forEach(actual::add);
    assertEquals(options, actual);
  }

  @Test
  public void testWithSocketOptionsAcceptsAnyIterable() {
    List<Inet.SocketOption> options = List.of(TcpSO.keepAlive(true), TcpSO.receiveBufferSize(1024));

    iterablesOf(options)
        .forEach(
            (shape, iterable) ->
                assertEquals(
                    options,
                    listOf(
                        ServerSettings.create("").withSocketOptions(iterable).getSocketOptions()),
                    "withSocketOptions(" + shape + ")"));
  }

  @Test
  public void testWithSocketOptionsReplacesExistingOptions() {
    ServerSettings settings =
        ServerSettings.create("").withSocketOptions(List.of(TcpSO.keepAlive(true)));

    assertEquals(
        List.of(TcpSO.tcpNoDelay(true)),
        listOf(settings.withSocketOptions(Set.of(TcpSO.tcpNoDelay(true))).getSocketOptions()));
    assertEquals(
        List.of(),
        listOf(
            settings
                .withSocketOptions(Collections.<Inet.SocketOption>emptySet())
                .getSocketOptions()));
  }

  @Test
  public void testWithSocketOptionsTraversesTheIterableOnce() {
    List<Inet.SocketOption> options = List.of(TcpSO.keepAlive(true));

    assertEquals(
        options,
        listOf(ServerSettings.create("").withSocketOptions(oneShot(options)).getSocketOptions()));
  }

  /** The same elements as different kinds of {@link Iterable}, keyed by a description. */
  private static <T> Map<String, Iterable<T>> iterablesOf(List<T> items) {
    Map<String, Iterable<T>> shapes = new LinkedHashMap<>();
    shapes.put("ArrayList", new ArrayList<>(items));
    shapes.put("LinkedHashSet", new LinkedHashSet<>(items));
    shapes.put("ArrayDeque", new ArrayDeque<>(items));
    shapes.put("unmodifiable Collection", Collections.unmodifiableCollection(items));
    // not a java.util.Collection at all, only an Iterable
    shapes.put("non-Collection Iterable", items::iterator);
    return shapes;
  }

  /** An {@link Iterable} that fails if it is traversed more than once. */
  private static <T> Iterable<T> oneShot(List<T> items) {
    AtomicBoolean traversed = new AtomicBoolean();
    return () -> {
      if (traversed.getAndSet(true)) {
        throw new IllegalStateException("Iterable traversed more than once");
      }
      return items.iterator();
    };
  }

  private static <T> List<T> listOf(Iterable<? extends T> iterable) {
    List<T> list = new ArrayList<>();
    iterable.forEach(list::add);
    return list;
  }
}
