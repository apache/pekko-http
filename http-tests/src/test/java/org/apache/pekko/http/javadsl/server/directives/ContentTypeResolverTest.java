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

package org.apache.pekko.http.javadsl.server.directives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpCharsets;
import org.apache.pekko.http.javadsl.model.MediaType;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.junit.jupiter.api.Test;

public class ContentTypeResolverTest {
  private final MediaType.Binary custom =
      MediaTypes.applicationBinary("x-custom", MediaTypes.NOT_COMPRESSIBLE);

  @Test
  public void testWithExtensionsResolvesCustomAndBuiltInExtensions() {
    Map<String, MediaType> extensions = new HashMap<>();
    extensions.put("custom", custom);
    ContentTypeResolver resolver = ContentTypeResolver.withExtensions(extensions);

    assertEquals(custom.toContentType(), resolver.resolve("file.CUSTOM"));
    assertEquals(ContentTypes.TEXT_HTML_UTF8, resolver.resolve("index.html"));
  }

  @Test
  public void testWithExtensionsUsesTheGivenDefaultCharset() {
    MediaType.WithOpenCharset customText =
        MediaTypes.customWithOpenCharset("text", "x-custom", new HashMap<>(), false);
    Map<String, MediaType> extensions = new HashMap<>();
    extensions.put("ctext", customText);
    ContentTypeResolver resolver =
        ContentTypeResolver.withExtensions(extensions, HttpCharsets.ISO_8859_1);

    assertEquals(customText.toContentType(HttpCharsets.ISO_8859_1), resolver.resolve("file.ctext"));
    assertEquals(
        MediaTypes.TEXT_HTML.toContentType(HttpCharsets.ISO_8859_1),
        resolver.resolve("index.html"));
  }

  @Test
  public void testWithExtensionsAcceptsAMapOfAMediaTypeSubtype() {
    Map<String, MediaType.Binary> extensions = new HashMap<>();
    extensions.put("custom", custom);

    assertEquals(
        custom.toContentType(), ContentTypeResolver.withExtensions(extensions).resolve("a.custom"));
    assertEquals(
        custom.toContentType(),
        ContentTypeResolver.withExtensions(extensions, HttpCharsets.ISO_8859_1)
            .resolve("a.custom"));
  }

  @Test
  public void testWithExtensionsRejectsInvalidExtensions() {
    Map<String, MediaType> extensions = new HashMap<>();
    extensions.put("gz", custom);
    assertThrows(
        IllegalArgumentException.class, () -> ContentTypeResolver.withExtensions(extensions));
  }
}
