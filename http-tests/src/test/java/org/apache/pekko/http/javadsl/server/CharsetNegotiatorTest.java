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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.apache.pekko.http.javadsl.model.HttpCharset;
import org.apache.pekko.http.javadsl.model.HttpCharsets;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.junit.jupiter.api.Test;

class CharsetNegotiatorTest {
  @Test
  void picksFromApplicationSupportedCharsets() {
    HttpRequest request =
        HttpRequest.GET("/")
            .addHeader(HttpHeader.parse("Accept-Charset", "UTF-8;q=0, ISO-8859-1;q=0.4, *;q=0.8"));

    List<HttpCharset> offered =
        List.of(HttpCharsets.UTF_8, HttpCharsets.UTF_16LE, HttpCharsets.ISO_8859_1);
    Optional<HttpCharset> selected = CharsetNegotiator.pickCharset(request.getHeaders(), offered);

    assertEquals(Optional.of(HttpCharsets.UTF_16LE), selected);
    assertEquals(
        Optional.empty(),
        CharsetNegotiator.pickCharset(request.getHeaders(), List.of(HttpCharsets.UTF_8)));
  }

  @Test
  void canEncodeAnEntityInTheSelectedCharset() {
    HttpRequest request =
        HttpRequest.GET("/").addHeader(HttpHeader.parse("Accept-Charset", "UTF-8;q=0, *;q=0.8"));

    // #pick-charset
    List<HttpCharset> offered = List.of(HttpCharsets.UTF_8, HttpCharsets.ISO_8859_1);
    Optional<HttpEntity.Strict> entity =
        CharsetNegotiator.pickCharset(request.getHeaders(), offered)
            .map(
                charset ->
                    HttpEntities.create(MediaTypes.TEXT_PLAIN.toContentType(charset), "café"));
    // #pick-charset

    HttpEntity.Strict selected = entity.orElseThrow();
    assertEquals(
        MediaTypes.TEXT_PLAIN.toContentType(HttpCharsets.ISO_8859_1), selected.getContentType());
    assertArrayEquals(new byte[] {'c', 'a', 'f', (byte) 0xe9}, selected.getData().toArray());
  }
}
