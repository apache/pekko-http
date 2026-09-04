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

import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extractMaterializer;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.testkit.JUnitJupiterRouteTest;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.Test;

public class RouteFlowMaterializerTest extends JUnitJupiterRouteTest {

  private final Route route =
      extractMaterializer(m -> complete(String.valueOf(System.identityHashCode(m))));

  private String runFlow(Flow<HttpRequest, HttpResponse, NotUsed> flow, Materializer mat)
      throws Exception {
    CompletionStage<HttpResponse> response =
        Source.single(HttpRequest.GET("/")).via(flow).runWith(Sink.head(), mat);
    return response
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS)
        .entity()
        .toStrict(5000, mat)
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS)
        .getData()
        .utf8String();
  }

  @Test
  public void flowUsesTheGivenMaterializer() throws Exception {
    Materializer custom = Materializer.createMaterializer(system());
    try {
      assertEquals(
          String.valueOf(System.identityHashCode(custom)),
          runFlow(route.flow(system(), custom), custom));
    } finally {
      custom.shutdown();
    }
  }

  @Test
  public void flowWithoutMaterializerUsesTheSystemMaterializer() throws Exception {
    Materializer systemMaterializer = SystemMaterializer.get(system()).materializer();
    assertEquals(
        String.valueOf(System.identityHashCode(systemMaterializer)),
        runFlow(route.flow(system()), systemMaterializer));
  }
}
