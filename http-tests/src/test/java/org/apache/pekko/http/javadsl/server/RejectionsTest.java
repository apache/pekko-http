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

package org.apache.pekko.http.javadsl.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.pekko.http.javadsl.model.ContentType;
import org.apache.pekko.http.javadsl.model.ContentTypeRange;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpMethods;
import org.apache.pekko.http.javadsl.model.MediaType;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.headers.ByteRange;
import org.junit.jupiter.api.Test;

public class RejectionsTest {

  @Test
  public void rejectedAcceptsIterableOfRejectionSubtype() {
    List<MethodRejection> rejections =
        List.of(Rejections.method(HttpMethods.GET), Rejections.method(HttpMethods.POST));

    Rejected rejected = RouteResults.rejected(rejections);

    List<Rejection> actual = new ArrayList<>();
    rejected.getRejections().forEach(actual::add);
    assertEquals(rejections, actual);
  }

  @Test
  public void unsupportedRequestContentTypeAcceptsMediaTypeAndContentTypeSubtypes() {
    List<MediaType.Binary> supported = List.of(MediaTypes.IMAGE_PNG);
    Optional<ContentType.WithFixedCharset> contentType = Optional.of(ContentTypes.APPLICATION_JSON);

    UnsupportedRequestContentTypeRejection withContentType =
        Rejections.unsupportedRequestContentType(supported, contentType);
    UnsupportedRequestContentTypeRejection withoutContentType =
        Rejections.unsupportedRequestContentType(supported);

    for (UnsupportedRequestContentTypeRejection rejection :
        List.of(withContentType, withoutContentType)) {
      assertEquals(1, rejection.getSupported().size());
      ContentTypeRange range = rejection.getSupported().iterator().next();
      assertTrue(
          range.matches(ContentTypes.create(MediaTypes.IMAGE_PNG)),
          () -> "expected supported range " + range + " to match image/png");
    }
    assertEquals(
        scala.Option.apply(ContentTypes.APPLICATION_JSON),
        ((org.apache.pekko.http.scaladsl.server.UnsupportedRequestContentTypeRejection)
                withContentType)
            .contentType());
  }

  @Test
  public void unsatisfiableRangeAcceptsIterableOfByteRangeSubtype() {
    List<org.apache.pekko.http.scaladsl.model.headers.ByteRange.Slice> ranges =
        List.of(org.apache.pekko.http.scaladsl.model.headers.ByteRange.apply(10, 20));

    UnsatisfiableRangeRejection rejection = Rejections.unsatisfiableRange(ranges, 5);

    List<ByteRange> actual = new ArrayList<>();
    rejection.getUnsatisfiableRanges().forEach(actual::add);
    assertEquals(List.of(ByteRange.createSlice(10, 20)), actual);
    assertEquals(5, rejection.actualEntityLength());
  }

  @Test
  public void rejectedAcceptsAnyIterable() {
    List<MethodRejection> rejections =
        List.of(Rejections.method(HttpMethods.GET), Rejections.method(HttpMethods.POST));

    iterablesOf(rejections)
        .forEach(
            (shape, iterable) ->
                assertEquals(
                    rejections,
                    listOf(RouteResults.rejected(iterable).getRejections()),
                    "rejected(" + shape + ")"));
  }

  @Test
  public void rejectedKeepsDifferentRejectionTypesInOrder() {
    List<Rejection> mixed =
        List.of(
            Rejections.method(HttpMethods.GET),
            Rejections.missingQueryParam("q"),
            Rejections.missingHeader("x-a"));

    assertEquals(mixed, listOf(RouteResults.rejected(new ArrayDeque<>(mixed)).getRejections()));
    assertEquals(
        List.of(),
        listOf(RouteResults.rejected(Collections.<Rejection>emptySet()).getRejections()));
  }

  @Test
  public void unsupportedRequestContentTypeAcceptsAnyIterable() {
    List<MediaType.Binary> supported = List.of(MediaTypes.IMAGE_PNG, MediaTypes.IMAGE_JPEG);
    UnsupportedRequestContentTypeRejection expected =
        Rejections.unsupportedRequestContentType(new ArrayList<>(supported), Optional.empty());

    iterablesOf(supported)
        .forEach(
            (shape, iterable) -> {
              UnsupportedRequestContentTypeRejection withContentType =
                  Rejections.unsupportedRequestContentType(iterable, Optional.empty());
              UnsupportedRequestContentTypeRejection withoutContentType =
                  Rejections.unsupportedRequestContentType(iterable);

              assertEquals(2, withContentType.getSupported().size(), shape);
              assertEquals(expected, withContentType, shape);
              assertEquals(expected, withoutContentType, shape);
            });
  }

  @Test
  public void unsatisfiableRangeAcceptsAnyIterable() {
    List<ByteRange> ranges =
        List.of(
            ByteRange.createSlice(10, 20),
            ByteRange.createSuffix(5),
            ByteRange.createFromOffset(30));

    iterablesOf(ranges)
        .forEach(
            (shape, iterable) ->
                assertEquals(
                    ranges,
                    listOf(Rejections.unsatisfiableRange(iterable, 5).getUnsatisfiableRanges()),
                    "unsatisfiableRange(" + shape + ")"));
  }

  @Test
  public void factoriesTraverseTheIterableOnce() {
    List<MethodRejection> rejections = List.of(Rejections.method(HttpMethods.GET));
    List<MediaType.Binary> supported = List.of(MediaTypes.IMAGE_PNG);
    List<ByteRange> ranges = List.of(ByteRange.createSlice(10, 20));

    assertEquals(rejections, listOf(RouteResults.rejected(oneShot(rejections)).getRejections()));
    assertEquals(
        1,
        Rejections.unsupportedRequestContentType(oneShot(supported), Optional.empty())
            .getSupported()
            .size());
    assertEquals(
        1, Rejections.unsupportedRequestContentType(oneShot(supported)).getSupported().size());
    assertEquals(
        ranges, listOf(Rejections.unsatisfiableRange(oneShot(ranges), 5).getUnsatisfiableRanges()));
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
