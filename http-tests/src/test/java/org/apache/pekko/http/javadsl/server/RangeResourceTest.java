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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpMethods;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.ByteRange;
import org.apache.pekko.http.javadsl.model.headers.Range;
import org.apache.pekko.http.javadsl.model.headers.RangeUnits;
import org.apache.pekko.http.javadsl.testkit.JUnitJupiterRouteTest;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.Test;

public class RangeResourceTest extends JUnitJupiterRouteTest {
  @Test
  public void buildsAStandalonePlanAndServesABoundedRouteWithTheSameProvider() {
    var opened = new AtomicInteger();
    var readOffset = new AtomicLong(-1);
    var readLength = new AtomicLong(-1);
    var data = ByteString.fromString("abcdefghijklmnopqrstuvwxyz");

    final class AlphabetReader implements RangeResource.Reader {
      @Override
      public Source<ByteString, ?> read(long offset, long length) {
        readOffset.set(offset);
        readLength.set(length);
        return Source.single(data.slice(Math.toIntExact(offset), Math.toIntExact(offset + length)));
      }
    }
    Supplier<CompletableFuture<AlphabetReader>> openReader =
        () -> {
          opened.incrementAndGet();
          return CompletableFuture.completedFuture(new AlphabetReader());
        };
    RangeResource resource =
        RangeResource.create(ContentTypes.TEXT_PLAIN_UTF8, data.size(), openReader);

    var range = Range.create(RangeUnits.BYTES, ByteRange.createSlice(4, 7));
    HttpResponse plan = resource.responseFor(HttpMethods.GET, List.of(range)).orElseThrow();
    assertEquals(StatusCodes.PARTIAL_CONTENT, plan.status());
    assertEquals(4L, plan.entity().getContentLengthOption().orElseThrow());
    assertEquals(0, opened.get());

    runRoute(getFromRangeResource(resource), HttpRequest.GET("/").addHeader(range))
        .assertStatusCode(StatusCodes.PARTIAL_CONTENT)
        .assertEntity("efgh");
    assertEquals(1, opened.get());
    assertEquals(4L, readOffset.get());
    assertEquals(4L, readLength.get());
  }
}
