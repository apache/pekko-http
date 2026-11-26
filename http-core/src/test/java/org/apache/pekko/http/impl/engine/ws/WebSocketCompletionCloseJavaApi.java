/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.impl.engine.ws;

import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.http.javadsl.model.ws.WebSocket;
import org.apache.pekko.http.javadsl.model.ws.WebSocketUpgrade;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

public final class WebSocketCompletionCloseJavaApi {
  private WebSocketCompletionCloseJavaApi() {}

  public static HttpResponse upgrade(WebSocketUpgrade upgrade, boolean selectProtocol) {
    Flow<Object, TextMessage, NotUsed> handler =
        Flow.fromSinkAndSource(Sink.<Object>ignore(), Source.single(TextMessage.create("ready")));
    HttpResponse response =
        selectProtocol
            ? upgrade.handleMessagesWith(handler, "chat")
            : upgrade.handleMessagesWith(handler);
    return WebSocket.withCompletionClose(response, 4200, "finished");
  }
}
