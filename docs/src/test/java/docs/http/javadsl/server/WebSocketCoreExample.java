/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server;

// #websocket-example-using-core

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pekko.NotUsed;
import org.apache.pekko.http.impl.util.JavaMapping;
import org.apache.pekko.http.javadsl.ConnectionContext;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.ws.WebSocketRequest;
import org.apache.pekko.http.javadsl.settings.ClientConnectionSettings;
import org.apache.pekko.http.javadsl.settings.ServerSettings;
import org.apache.pekko.http.javadsl.settings.WebSocketSettings;
import org.apache.pekko.http.scaladsl.model.AttributeKeys;
import org.apache.pekko.japi.JavaPartialFunction;
import org.apache.pekko.japi.function.Function;

import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.http.javadsl.model.ws.WebSocket;
import org.apache.pekko.util.ByteString;

@SuppressWarnings({"Convert2MethodRef", "ConstantConditions"})
public class WebSocketCoreExample {

  // #websocket-handling
  public static HttpResponse handleRequest(HttpRequest request) {
    System.out.println("Handling request to " + request.getUri());

    if (request.getUri().path().equals("/greeter")) {
      return request
          .getAttribute(AttributeKeys.webSocketUpgrade())
          .map(
              upgrade -> {
                Flow<Message, Message, NotUsed> greeterFlow = greeter();
                HttpResponse response = upgrade.handleMessagesWith(greeterFlow);
                return response;
              })
          .orElse(
              HttpResponse.create()
                  .withStatus(StatusCodes.BAD_REQUEST)
                  .withEntity("Expected WebSocket request"));
    } else {
      return HttpResponse.create().withStatus(404);
    }
  }
  // #websocket-handling

  public static void main(String[] args) throws Exception {
    ActorSystem system = ActorSystem.create();

    try {
      final Function<HttpRequest, HttpResponse> handler = request -> handleRequest(request);
      CompletionStage<ServerBinding> serverBindingFuture =
          Http.get(system).newServerAt("localhost", 8080).bindSync(handler);

      // will throw if binding fails
      serverBindingFuture.toCompletableFuture().get(1, TimeUnit.SECONDS);
      System.out.println("Press ENTER to stop.");
      new BufferedReader(new InputStreamReader(System.in)).readLine();
    } finally {
      system.terminate();
    }
  }

  // #websocket-handler

  /**
   * A handler that treats incoming messages as a name, and responds with a greeting to that name
   */
  public static Flow<Message, Message, NotUsed> greeter() {
    return Flow.<Message>create()
        .collect(
            new JavaPartialFunction<Message, Message>() {
              @Override
              public Message apply(Message msg, boolean isCheck) throws Exception {
                if (isCheck) {
                  if (msg.isText()) {
                    return null;
                  } else {
                    throw noMatch();
                  }
                } else {
                  return handleTextMessage(msg.asTextMessage());
                }
              }
            });
  }

  public static TextMessage handleTextMessage(TextMessage msg) {
    if (msg.isStrict()) // optimization that directly creates a simple response...
    {
      return TextMessage.create("Hello " + msg.getStrictText());
    } else // ... this would suffice to handle all text messages in a streaming fashion
    {
      return TextMessage.create(Source.single("Hello ").concat(msg.getStreamedText()));
    }
  }
  // #websocket-handler

  {
    ActorSystem system = null;
    Flow<HttpRequest, HttpResponse, NotUsed> handler = null;
    // #websocket-ping-payload-server
    ServerSettings defaultSettings = ServerSettings.create(system);

    AtomicInteger pingCounter = new AtomicInteger();

    WebSocketSettings customWebsocketSettings =
        defaultSettings
            .getWebsocketSettings()
            .withPeriodicKeepAliveData(
                () ->
                    ByteString.fromString(
                        String.format("debug-%d", pingCounter.incrementAndGet())));

    ServerSettings customServerSettings =
        defaultSettings.withWebsocketSettings(customWebsocketSettings);

    Http http = Http.get(system);
    http.newServerAt("127.0.0.1", 8080).withSettings(customServerSettings).bindFlow(handler);
    // #websocket-ping-payload-server
  }

  {
    ActorSystem system = null;
    Materializer materializer = null;
    Flow<Message, Message, NotUsed> clientFlow = null;
    // #websocket-client-ping-payload
    ClientConnectionSettings defaultSettings = ClientConnectionSettings.create(system);

    AtomicInteger pingCounter = new AtomicInteger();

    WebSocketSettings customWebsocketSettings =
        defaultSettings
            .getWebsocketSettings()
            .withPeriodicKeepAliveData(
                () ->
                    ByteString.fromString(
                        String.format("debug-%d", pingCounter.incrementAndGet())));

    ClientConnectionSettings customSettings =
        defaultSettings.withWebsocketSettings(customWebsocketSettings);

    Http http = Http.get(system);
    http.singleWebSocketRequest(
        WebSocketRequest.create("ws://127.0.0.1"),
        clientFlow,
        ConnectionContext.noEncryption(),
        Optional.empty(),
        customSettings,
        system.log(),
        materializer);
    // #websocket-client-ping-payload
  }
}
// #websocket-example-using-core
