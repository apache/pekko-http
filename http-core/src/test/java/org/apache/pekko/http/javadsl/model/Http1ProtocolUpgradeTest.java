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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.Test;

class Http1ProtocolUpgradeTest {

  @Test
  void acceptsAPublicHttp1OfferWithTheOriginalRequest() throws Exception {
    assertTrue(Http1ProtocolUpgrade.fromRequest(HttpRequest.create()).isEmpty());
    ActorSystem system = ActorSystem.create("http1-protocol-upgrade-java");
    try {
      ServerBinding binding =
          Http.get(system)
              .newServerAt("127.0.0.1", 0, "example-java")
              .adaptSettings(settings -> settings.withEnableHttp2(false))
              .bindSync(
                  request ->
                      Http1ProtocolUpgrade.fromRequest(request)
                          .map(
                              offer ->
                                  offer.accept(
                                      original ->
                                          Flow.of(ByteString.class)
                                              .take(0)
                                              .prepend(
                                                  Source.single(
                                                      ByteString.fromString(
                                                          original.method().value()
                                                              + " "
                                                              + original.getUri().path()
                                                              + "\n")))))
                          .orElse(HttpResponse.create()))
              .toCompletableFuture()
              .get(8, TimeUnit.SECONDS);
      try (Socket socket = new Socket()) {
        socket.connect(binding.localAddress(), 5000);
        socket.setSoTimeout(5000);
        String request =
            "OPTIONS /features HTTP/1.1\r\nHost: 127.0.0.1:"
                + binding.localAddress().getPort()
                + "\r\nConnection: Upgrade\r\nUpgrade: example-java\r\n\r\n";
        socket.getOutputStream().write(request.getBytes(UTF_8));
        socket.getOutputStream().flush();
        BufferedReader input =
            new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
        assertEquals("HTTP/1.1 101 Switching Protocols", input.readLine());
        List<String> headers = new ArrayList<>();
        for (String line; (line = input.readLine()) != null && !line.isEmpty(); ) {
          headers.add(line);
        }
        assertTrue(headers.contains("Upgrade: example-java"));
        assertTrue(headers.contains("Connection: Upgrade"));
        assertEquals("OPTIONS /features", input.readLine());
      } finally {
        binding.terminate(Duration.ofSeconds(1)).toCompletableFuture().get(8, TimeUnit.SECONDS);
      }
    } finally {
      TestKit.shutdownActorSystem(system);
    }
  }
}
