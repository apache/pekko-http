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
}
