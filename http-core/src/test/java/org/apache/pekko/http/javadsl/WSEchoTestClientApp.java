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

package org.apache.pekko.http.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.http.javadsl.model.ws.WebSocketRequest;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class WSEchoTestClientApp {
  private static final Function<Message, String> messageStringifier =
      new Function<Message, String>() {
        private static final long serialVersionUID = 1L;

        @Override
        public String apply(Message msg) throws Exception {
          if (msg.isText() && msg.asTextMessage().isStrict())
            return msg.asTextMessage().getStrictText();
          else throw new IllegalArgumentException("Unexpected message " + msg);
        }
      };

  public static void main(String[] args) throws Exception {
    ActorSystem system = ActorSystem.create();

    try {
      final Materializer materializer = Materializer.createMaterializer(system);

      final CompletableFuture<Message> ignoredMessage =
          CompletableFuture.completedFuture((Message) TextMessage.create("blub"));
      final CompletionStage<Message> delayedCompletion =
          org.apache.pekko.pattern.Patterns.after(
              Duration.ofSeconds(1), system.scheduler(), system.dispatcher(), () -> ignoredMessage);

      Source<Message, NotUsed> echoSource =
          Source.from(
                  Arrays.<Message>asList(
                      TextMessage.create("abc"),
                      TextMessage.create("def"),
                      TextMessage.create("ghi")))
              .concat(Source.completionStage(delayedCompletion).drop(1));

      Sink<Message, CompletionStage<List<String>>> echoSink =
          Flow.of(Message.class)
              .map(messageStringifier)
              .grouped(1000)
              .toMat(Sink.<List<String>>head(), Keep.right());

      Flow<Message, Message, CompletionStage<List<String>>> echoClient =
          Flow.fromSinkAndSourceMat(echoSink, echoSource, Keep.left());

      CompletionStage<List<String>> result =
          Http.get(system)
              .singleWebSocketRequest(
                  WebSocketRequest.create("ws://echo.websocket.org"), echoClient, materializer)
              .second();

      List<String> messages = result.toCompletableFuture().get(10, TimeUnit.SECONDS);
      System.out.println("Collected " + messages.size() + " messages:");
      for (String msg : messages) System.out.println(msg);
    } finally {
      system.terminate();
    }
  }
}
