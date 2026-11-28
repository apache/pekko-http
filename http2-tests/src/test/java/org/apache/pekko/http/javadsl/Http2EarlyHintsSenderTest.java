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

package org.apache.pekko.http.javadsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.typesafe.config.ConfigFactory;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.EarlyHintsSender;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.settings.ServerSettings;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.jupiter.api.Test;

public class Http2EarlyHintsSenderTest {

  @Test
  public void usesEarlyHintsFromAnHttp2HandlerAndPreservesHttp1Fallback() throws Exception {
    ActorSystem system =
        ActorSystem.create(
            "Http2EarlyHintsSenderTest",
            ConfigFactory.parseString("pekko.http.server.http2.early-hints-enabled = on"));
    Materializer mat = SystemMaterializer.get(system).materializer();
    ServerBinding binding = null;
    try {
      ServerSettings defaults = ServerSettings.create(system).withEnableHttp2(true);
      Http http = Http.get(system);
      List<RawHeader> hints = List.of(RawHeader.create("Link", "</main.css>; rel=preload"));
      Function<HttpRequest, CompletionStage<HttpResponse>> handler =
          request ->
              EarlyHintsSender.get(request)
                  .map(
                      sender ->
                          sender
                              .send(hints)
                              .thenApply(done -> HttpResponse.create().withEntity("h2")))
                  .orElseGet(
                      () ->
                          CompletableFuture.completedFuture(
                              HttpResponse.create().withEntity("h1")));
      binding =
          http.newServerAt("127.0.0.1", 0)
              .withSettings(defaults)
              .bind(handler)
              .toCompletableFuture()
              .get(5, TimeUnit.SECONDS);

      OutgoingConnectionBuilder connection =
          http.connectionTo("127.0.0.1").toPort(binding.localAddress().getPort());
      String h2 =
          Source.single(HttpRequest.create("/index"))
              .via(connection.http2WithPriorKnowledge())
              .mapAsync(1, response -> readBody(response, mat))
              .runWith(Sink.head(), mat)
              .toCompletableFuture()
              .get(5, TimeUnit.SECONDS);
      assertEquals("h2", h2);

      String h1 =
          Source.single(HttpRequest.create("/index"))
              .via(connection.http())
              .mapAsync(1, response -> readBody(response, mat))
              .runWith(Sink.head(), mat)
              .toCompletableFuture()
              .get(5, TimeUnit.SECONDS);
      assertEquals("h1", h1);
    } finally {
      try {
        if (binding != null) binding.unbind().toCompletableFuture().get(5, TimeUnit.SECONDS);
      } finally {
        TestKit.shutdownActorSystem(system);
      }
    }
  }

  private static CompletionStage<String> readBody(HttpResponse response, Materializer mat) {
    assertEquals(200, response.status().intValue());
    return response.entity().toStrict(3000, mat).thenApply(entity -> entity.getData().utf8String());
  }
}
