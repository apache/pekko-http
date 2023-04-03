/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

/**
 * Contains constants of the supported Http protocols.
 */
public final class HttpProtocols {
    private HttpProtocols() {}

    public final static HttpProtocol HTTP_1_0 = org.apache.pekko.http.scaladsl.model.HttpProtocols.HTTP$div1$u002E0();
    public final static HttpProtocol HTTP_1_1 = org.apache.pekko.http.scaladsl.model.HttpProtocols.HTTP$div1$u002E1();
}
