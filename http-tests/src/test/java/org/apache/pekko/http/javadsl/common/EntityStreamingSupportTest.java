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

package org.apache.pekko.http.javadsl.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.http.javadsl.testkit.JUnitJupiterRouteTest;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.Test;

public class EntityStreamingSupportTest extends JUnitJupiterRouteTest {

  @Test
  public void withFramingRendererFlowAcceptsAnyMaterializedValue() throws Exception {
    Flow<ByteString, ByteString, String> renderer =
        Flow.of(ByteString.class)
            .intersperse(
                ByteString.fromString("<"), ByteString.fromString("|"), ByteString.fromString(">"))
            .mapMaterializedValue(notUsed -> "ignored");

    List<EntityStreamingSupport> supports =
        List.of(
            EntityStreamingSupport.json().withFramingRendererFlow(renderer),
            EntityStreamingSupport.csv().withFramingRendererFlow(renderer));

    for (EntityStreamingSupport support : supports) {
      ByteString rendered =
          Source.from(List.of(ByteString.fromString("a"), ByteString.fromString("b")))
              .via(support.getFramingRenderer())
              .runFold(ByteString.emptyByteString(), ByteString::concat, materializer())
              .toCompletableFuture()
              .get(3, TimeUnit.SECONDS);

      assertEquals("<a|b>", rendered.utf8String(), support.getClass().getSimpleName());
    }
  }
}
