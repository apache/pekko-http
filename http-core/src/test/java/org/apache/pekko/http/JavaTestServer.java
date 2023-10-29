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

package org.apache.pekko.http;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.JavaApiTestCases;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.http.javadsl.model.ws.WebSocket;
import org.apache.pekko.japi.JavaPartialFunction;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class JavaTestServer {
  public static void main(String[] args) throws Exception {
    ActorSystem system = ActorSystem.create();

    try {
      CompletionStage<ServerBinding> serverBindingFuture =
          Http.get(system)
              .newServerAt("localhost", 8080)
              .bindSync(
                  request -> {
                    System.out.println("Handling request to " + request.getUri());

                    if (request.getUri().path().equals("/"))
                      return WebSocket.handleWebSocketRequestWith(request, echoMessages());
                    else if (request.getUri().path().equals("/greeter"))
                      return WebSocket.handleWebSocketRequestWith(request, greeter());
                    else return JavaApiTestCases.handleRequest(request);
                  });

      serverBindingFuture
          .toCompletableFuture()
          .get(1, TimeUnit.SECONDS); // will throw if binding fails
      System.out.println("Press ENTER to stop.");
      new BufferedReader(new InputStreamReader(System.in)).readLine();
    } finally {
      system.terminate();
    }
  }

  public static Flow<Message, Message, NotUsed> echoMessages() {
    return Flow.create(); // the identity operation
  }

  public static Flow<Message, Message, NotUsed> greeter() {
    return Flow.<Message>create()
        .collect(
            new JavaPartialFunction<Message, Message>() {
              @Override
              public Message apply(Message msg, boolean isCheck) throws Exception {
                if (isCheck)
                  if (msg.isText()) return null;
                  else throw noMatch();
                else return handleTextMessage(msg.asTextMessage());
              }
            });
  }

  public static TextMessage handleTextMessage(TextMessage msg) {
    if (msg.isStrict()) return TextMessage.create("Hello " + msg.getStrictText());
    else return TextMessage.create(Source.single("Hello ").concat(msg.getStreamedText()));
  }
}
