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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.junit.jupiter.api.Test;
import scala.Tuple2;
import scala.jdk.javaapi.CollectionConverters;

public class TrailerTest {

  private static List<Tuple2<String, String>> headersOf(Trailer trailer) {
    return CollectionConverters.asJava(
        ((org.apache.pekko.http.scaladsl.model.Trailer) trailer).headers());
  }

  @Test
  public void addHeadersAcceptsJavaIterable() {
    Trailer trailer =
        Trailer.create()
            .addHeader(RawHeader.create("a", "1"))
            .addHeaders(List.of(RawHeader.create("b", "2"), RawHeader.create("c", "3")));

    assertEquals(
        List.of(new Tuple2<>("a", "1"), new Tuple2<>("b", "2"), new Tuple2<>("c", "3")),
        headersOf(trailer));
  }

  @Test
  public void withHeadersAcceptsJavaIterable() {
    List<HttpHeader> replacement = new ArrayList<>();
    replacement.add(RawHeader.create("b", "2"));

    Trailer trailer =
        Trailer.create().addHeader(RawHeader.create("a", "1")).withHeaders(replacement);

    assertEquals(List.of(new Tuple2<>("b", "2")), headersOf(trailer));
  }

  @Test
  public void acceptsArrayListOfHttpHeader() {
    ArrayList<HttpHeader> headers = new ArrayList<>();
    headers.add(RawHeader.create("b", "2"));

    Trailer added = Trailer.create().addHeader(RawHeader.create("a", "1")).addHeaders(headers);
    Trailer replaced = Trailer.create().addHeader(RawHeader.create("a", "1")).withHeaders(headers);

    assertEquals(List.of(new Tuple2<>("a", "1"), new Tuple2<>("b", "2")), headersOf(added));
    assertEquals(List.of(new Tuple2<>("b", "2")), headersOf(replaced));
  }

  @Test
  public void acceptsIterableOfHttpHeaderSubtype() {
    List<RawHeader> rawHeaders = List.of(RawHeader.create("b", "2"));

    Trailer added = Trailer.create().addHeader(RawHeader.create("a", "1")).addHeaders(rawHeaders);
    Trailer replaced =
        Trailer.create().addHeader(RawHeader.create("a", "1")).withHeaders(rawHeaders);

    assertEquals(List.of(new Tuple2<>("a", "1"), new Tuple2<>("b", "2")), headersOf(added));
    assertEquals(List.of(new Tuple2<>("b", "2")), headersOf(replaced));
  }

  @Test
  public void addAndWithHeadersAcceptAnyIterable() {
    List<RawHeader> headers = List.of(RawHeader.create("b", "2"), RawHeader.create("c", "3"));

    iterablesOf(headers)
        .forEach(
            (shape, iterable) -> {
              Trailer added =
                  Trailer.create().addHeader(RawHeader.create("a", "1")).addHeaders(iterable);
              Trailer replaced =
                  Trailer.create().addHeader(RawHeader.create("a", "1")).withHeaders(iterable);

              assertEquals(
                  List.of(new Tuple2<>("a", "1"), new Tuple2<>("b", "2"), new Tuple2<>("c", "3")),
                  headersOf(added),
                  "addHeaders(" + shape + ")");
              assertEquals(
                  List.of(new Tuple2<>("b", "2"), new Tuple2<>("c", "3")),
                  headersOf(replaced),
                  "withHeaders(" + shape + ")");
            });
  }

  @Test
  public void addAndWithHeadersTraverseTheIterableOnce() {
    List<RawHeader> headers = List.of(RawHeader.create("a", "1"));

    assertEquals(
        List.of(new Tuple2<>("a", "1")), headersOf(Trailer.create().addHeaders(oneShot(headers))));
    assertEquals(
        List.of(new Tuple2<>("a", "1")), headersOf(Trailer.create().withHeaders(oneShot(headers))));
  }

  @Test
  public void withHeadersOfEmptyIterableRemovesAllHeaders() {
    Trailer trailer =
        Trailer.create()
            .addHeader(RawHeader.create("a", "1"))
            .withHeaders(Collections.<RawHeader>emptySet());

    assertEquals(List.of(), headersOf(trailer));
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
