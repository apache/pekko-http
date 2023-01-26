/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

import org.apache.pekko.http.javadsl.model.ws.WebSocketUpgrade;

public final class AttributeKeys {
    public static final AttributeKey<RemoteAddress> remoteAddress =
            (AttributeKey<RemoteAddress>)(Object)org.apache.pekko.http.scaladsl.model.AttributeKeys.remoteAddress();
    public static final AttributeKey<WebSocketUpgrade> webSocketUpgrade =
            (AttributeKey<WebSocketUpgrade>)(Object)org.apache.pekko.http.scaladsl.model.AttributeKeys.webSocketUpgrade();
    public static final AttributeKey<SslSessionInfo> sslSession =
            (AttributeKey<SslSessionInfo>)(Object)org.apache.pekko.http.scaladsl.model.AttributeKeys.sslSession();
    public static final AttributeKey<Trailer> trailer =
            (AttributeKey<Trailer>)(Object)org.apache.pekko.http.scaladsl.model.AttributeKeys.trailer();
}
