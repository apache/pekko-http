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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.Uri;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.http.javadsl.model.ws.WebSocket;
import org.apache.pekko.http.javadsl.model.ws.WebSocketRequest;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitJupiterRouteTest;
import org.apache.pekko.http.javadsl.testkit.WSProbe;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.Test;

/**
 * The Java WebSocket APIs accept handlers whose element types are subtypes or supertypes of {@link
 * Message}, e.g. a {@code Flow<Message, TextMessage, NotUsed>}.
 */
public class WebSocketHandlerTypesTest extends JUnitJupiterRouteTest {

  // what Flow.of(Message.class).map(...) infers when the function returns a TextMessage
  private static final Flow<Message, TextMessage, NotUsed> upperCase =
      Flow.of(Message.class)
          .map(msg -> TextMessage.create(msg.asTextMessage().getStrictText().toUpperCase()));

  private static final Source<TextMessage, NotUsed> greeting =
      Source.single(TextMessage.create("hello"))
          .concat(Source.<TextMessage>maybe().mapMaterializedValue(f -> NotUsed.getInstance()));

  private final Route route =
      concat(
          path("directive", () -> handleWebSocketMessages(upperCase)),
          path("compress", () -> handleWebSocketMessages(upperCase, msg -> true)),
          path("protocol", () -> handleWebSocketMessagesForProtocol(upperCase, "echo")),
          path(
              "protocol-compress",
              () -> handleWebSocketMessagesForProtocol(upperCase, "echo", msg -> false)),
          path(
              "optional-protocol",
              () -> handleWebSocketMessagesForOptionalProtocol(upperCase, Optional.of("echo"))),
          path(
              "optional-protocol-compress",
              () ->
                  handleWebSocketMessagesForOptionalProtocol(
                      upperCase, Optional.empty(), msg -> true)),
          path(
              "upgrade-flow",
              () ->
                  extractWebSocketUpgrade(
                      upgrade -> complete(upgrade.handleMessagesWith(upperCase)))),
          path(
              "upgrade-sink-source",
              () ->
                  extractWebSocketUpgrade(
                      // Sink.ignore() is a Sink<Object, ...>: a supertype of Message on the input
                      // side
                      upgrade -> complete(upgrade.handleMessagesWith(Sink.ignore(), greeting)))),
          path(
              "core",
              () ->
                  extractRequest(
                      request ->
                          complete(WebSocket.handleWebSocketRequestWith(request, upperCase)))));

  @Test
  public void serverSideHandlersAcceptMessageSubtypes() {
    String[] paths = {
      "/directive",
      "/compress",
      "/protocol",
      "/protocol-compress",
      "/optional-protocol",
      "/optional-protocol-compress",
      "/upgrade-flow",
      "/core"
    };
    for (String path : paths) {
      WSProbe probe = WSProbe.create(system(), materializer());
      testRoute(route)
          .run(WS(Uri.create(path), probe.flow(), materializer(), List.of("echo")))
          .assertStatusCode(StatusCodes.SWITCHING_PROTOCOLS);

      probe.sendMessage("hi");
      probe.expectMessage("HI");
      probe.sendCompletion();
      probe.expectCompletion();
    }
  }

  @Test
  public void upgradeSinkAndSourceAcceptMessageSubtypeAndSupertype() {
    WSProbe probe = WSProbe.create(system(), materializer());
    testRoute(route)
        .run(WS(Uri.create("/upgrade-sink-source"), probe.flow(), materializer()))
        .assertStatusCode(StatusCodes.SWITCHING_PROTOCOLS);

    probe.expectMessage("hello");
  }

  @Test
  public void testkitClientHandlerAcceptsMessageSubtype() throws Exception {
    CompletableFuture<Message> reply = new CompletableFuture<>();
    Flow<Message, TextMessage, NotUsed> client =
        Flow.fromSinkAndSource(Sink.<Message>foreach(reply::complete), clientSource("hi"));

    testRoute(route)
        .run(WS(Uri.create("/directive"), client, materializer()))
        .assertStatusCode(StatusCodes.SWITCHING_PROTOCOLS);

    assertEquals("HI", reply.get(3, TimeUnit.SECONDS).asTextMessage().getStrictText());
  }

  @Test
  public void singleWebSocketRequestAcceptsMessageSubtype() throws Exception {
    Http http = Http.get(system());
    ServerBinding binding =
        http.newServerAt("127.0.0.1", 0).bind(route).toCompletableFuture().get(3, TimeUnit.SECONDS);
    try {
      Flow<Message, TextMessage, CompletionStage<Message>> client =
          Flow.fromSinkAndSourceMat(Sink.<Message>head(), clientSource("hi"), Keep.left());

      Pair<?, CompletionStage<Message>> result =
          http.singleWebSocketRequest(
              WebSocketRequest.create(
                  "ws://127.0.0.1:" + binding.localAddress().getPort() + "/directive"),
              client,
              materializer());

      assertEquals(
          "HI",
          result
              .second()
              .toCompletableFuture()
              .get(3, TimeUnit.SECONDS)
              .asTextMessage()
              .getStrictText());
    } finally {
      binding.unbind().toCompletableFuture().get(3, TimeUnit.SECONDS);
    }
  }

  // keeps the client side open after sending, so the server's reply is received
  private static Source<TextMessage, NotUsed> clientSource(String text) {
    return Source.single(TextMessage.create(text))
        .concat(Source.<TextMessage>maybe().mapMaterializedValue(f -> NotUsed.getInstance()));
  }
}
