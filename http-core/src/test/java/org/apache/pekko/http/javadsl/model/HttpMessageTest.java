/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import org.apache.pekko.http.javadsl.model.headers.Location;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.junit.jupiter.api.Test;

public class HttpMessageTest {
  @Test
  public void testRetrieveAttributeByKey() {
    AttributeKey<String> oneStringKey = AttributeKey.create("one", String.class);
    // keys with the same type but different names should be considered different
    AttributeKey<String> otherStringKey = AttributeKey.create("other", String.class);

    // it should be possible to use 'Scala attribute keys' in the Java API's
    AttributeKey<Integer> intKey = AttributeKey.create("int", Integer.class);
    // keys with the same name but different types should be considered different
    AttributeKey<Integer> otherIntKey = AttributeKey.create("other", Integer.class);

    String oneString = "A string attribute!";
    String otherString = "Another";
    Integer integer = 42;
    Integer otherInteger = 37;

    HttpRequest request =
        HttpRequest.create()
            .addAttribute(oneStringKey, oneString)
            .addAttribute(otherStringKey, otherString)
            .addAttribute(intKey, integer)
            .addAttribute(otherIntKey, otherInteger);

    assertEquals(oneString, request.getAttribute(oneStringKey).get());
    assertEquals(otherString, request.getAttribute(otherStringKey).get());
    assertEquals(integer, request.getAttribute(intKey).get());
    assertEquals(otherInteger, request.getAttribute(otherIntKey).get());

    HttpRequest smaller = request.removeAttribute(intKey);
    assertEquals(otherString, smaller.getAttribute(otherStringKey).get());
    assertFalse(smaller.getAttribute(intKey).isPresent());
  }

  @Test
  public void testAttributeKeys() {
    HttpRequest request = HttpRequest.create();
    RemoteAddress remoteAddress = RemoteAddress.create(new byte[] {10, 0, 0, 1});
    HttpRequest newRequest = request.addAttribute(AttributeKeys.remoteAddress, remoteAddress);

    assertTrue(newRequest.getAttribute(AttributeKeys.remoteAddress).get().equals(remoteAddress));
  }

  @Test
  public void testSslAttribute() throws Exception {
    SSLSession session = SSLContext.getDefault().createSSLEngine().getSession();
    HttpRequest request =
        HttpRequest.create().addAttribute(AttributeKeys.sslSession, SslSessionInfo.create(session));

    assertTrue(request.getAttribute(AttributeKeys.sslSession).get().getSession() == session);
  }

  @Test
  public void testAddAndWithHeadersAcceptIterableOfHeaderSubtype() {
    List<RawHeader> headers = List.of(RawHeader.create("x-b", "2"), RawHeader.create("x-c", "3"));

    HttpRequest added =
        HttpRequest.create().addHeader(RawHeader.create("x-a", "1")).addHeaders(headers);
    HttpResponse replaced =
        HttpResponse.create().addHeader(RawHeader.create("x-a", "1")).withHeaders(headers);

    assertEquals(
        List.of(
            RawHeader.create("x-a", "1"),
            RawHeader.create("x-b", "2"),
            RawHeader.create("x-c", "3")),
        added.getHeaders());
    assertEquals(headers, replaced.getHeaders());
  }

  @Test
  public void testAddAndWithHeadersAcceptAnyIterable() {
    RawHeader a = RawHeader.create("x-a", "1");
    List<RawHeader> headers = List.of(RawHeader.create("x-b", "2"), RawHeader.create("x-c", "3"));

    iterablesOf(headers)
        .forEach(
            (shape, iterable) -> {
              HttpRequest addedToRequest = HttpRequest.create().addHeader(a).addHeaders(iterable);
              HttpResponse addedToResponse =
                  HttpResponse.create().addHeader(a).addHeaders(iterable);
              HttpRequest replacedOnRequest =
                  HttpRequest.create().addHeader(a).withHeaders(iterable);
              HttpResponse replacedOnResponse =
                  HttpResponse.create().addHeader(a).withHeaders(iterable);

              List<HttpHeader> expectedAdded = List.of(a, headers.get(0), headers.get(1));
              assertEquals(expectedAdded, addedToRequest.getHeaders(), "addHeaders(" + shape + ")");
              assertEquals(
                  expectedAdded, addedToResponse.getHeaders(), "addHeaders(" + shape + ")");
              assertEquals(headers, replacedOnRequest.getHeaders(), "withHeaders(" + shape + ")");
              assertEquals(headers, replacedOnResponse.getHeaders(), "withHeaders(" + shape + ")");
            });
  }

  @Test
  public void testAddAndWithHeadersTraverseTheIterableOnce() {
    List<RawHeader> headers = List.of(RawHeader.create("x-a", "1"));

    assertEquals(headers, HttpRequest.create().addHeaders(oneShot(headers)).getHeaders());
    assertEquals(headers, HttpRequest.create().withHeaders(oneShot(headers)).getHeaders());
  }

  @Test
  public void testWithHeadersOfEmptyIterableRemovesAllHeaders() {
    HttpRequest request =
        HttpRequest.create()
            .addHeader(RawHeader.create("x-a", "1"))
            .withHeaders(Collections.<RawHeader>emptySet());

    assertEquals(List.of(), request.getHeaders());
  }

  @Test
  public void testAddHeadersKeepsDifferentHeaderTypesInOrder() {
    Location location = Location.create("/elsewhere");
    RawHeader raw = RawHeader.create("x-a", "1");
    Iterable<HttpHeader> mixed = new LinkedHashSet<>(List.of(location, raw));

    HttpResponse response = HttpResponse.create().addHeaders(mixed);

    assertEquals(List.of(location, raw), response.getHeaders());
    assertEquals(location, response.getHeader(Location.class).get());
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
}
