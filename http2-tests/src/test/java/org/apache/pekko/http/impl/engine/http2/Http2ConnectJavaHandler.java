/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.impl.engine.http2;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.AttributeKeys;
import org.apache.pekko.http.javadsl.model.Http2ConnectData;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.settings.ServerSettings;
import org.apache.pekko.util.ByteString;

public final class Http2ConnectJavaHandler {
  private Http2ConnectJavaHandler() {}

  public static ServerSettings settings(ActorSystem system, int connectionWindowSize) {
    ServerSettings defaults = ServerSettings.create(system);
    return defaults.withHttp2Settings(
        defaults
            .getHttp2Settings()
            .withAcceptConnectRequests(true)
            .withIncomingConnectionLevelBufferSize(connectionWindowSize));
  }

  public static CompletionStage<HttpResponse> echo(HttpRequest request) {
    Http2ConnectData tunnel = request.getAttribute(AttributeKeys.http2ConnectData).orElseThrow();
    HttpResponse response =
        tunnel.accept(tunnel.getData().map(data -> ByteString.fromString("local:").concat(data)));
    return CompletableFuture.completedFuture(response);
  }
}
