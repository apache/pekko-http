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

import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.japi.JavaPartialFunction;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;

public class WebSocketRoutingExample extends AllDirectives {

  // #websocket-route
  public Route createRoute() {
    return path("greeter", () -> handleWebSocketMessages(greeter()));
  }
  // #websocket-route

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
                  if (msg.isText()) return null;
                  else throw noMatch();
                } else return handleTextMessage(msg.asTextMessage());
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
}
