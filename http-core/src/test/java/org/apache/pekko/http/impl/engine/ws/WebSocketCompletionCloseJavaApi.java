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
