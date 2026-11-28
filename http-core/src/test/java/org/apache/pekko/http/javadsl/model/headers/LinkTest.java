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

package org.apache.pekko.http.javadsl.model.headers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Optional;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.Uri;
import org.junit.jupiter.api.Test;

public class LinkTest {

  @Test
  public void linkExtensionCanBeConstructedAndReadFromJava() {
    var as = LinkParams.extension("As", "style");
    var enabled = LinkParams.extension("x-enabled");
    var empty = LinkParams.extension("x-empty", "");
    assertEquals("as", as.key());

    var link =
        Link.create(
            LinkValue.create(
                Uri.create("/site.css"), LinkParams.rel("preload"), as, enabled, empty));
    assertEquals("</site.css>; rel=preload; as=style; x-enabled; x-empty=\"\"", link.value());
    var parsed = assertInstanceOf(Link.class, HttpHeader.parse("Link", link.value()));
    var params = parsed.getValues().iterator().next().getParams().iterator();
    assertEquals(LinkParams.rel("preload"), params.next());
    assertExtension(params.next(), "as", Optional.of("style"));
    assertExtension(params.next(), "x-enabled", Optional.empty());
    assertExtension(params.next(), "x-empty", Optional.of(""));
    assertFalse(params.hasNext());
  }

  private static void assertExtension(LinkParam param, String key, Optional<String> value) {
    assertEquals(key, param.key());
    assertEquals(value, assertInstanceOf(LinkParam.Extension.class, param).getValueOption());
  }
}
