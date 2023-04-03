/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.Authority;

import java.net.InetSocketAddress;

public abstract class Host extends org.apache.pekko.http.scaladsl.model.HttpHeader {

    public static Host create(InetSocketAddress address) {
        return org.apache.pekko.http.scaladsl.model.headers.Host.apply(address);
    }

    public static Host create(String host) {
        return org.apache.pekko.http.scaladsl.model.headers.Host.apply(host);
    }

    public static Host create(String host, int port) {
      return org.apache.pekko.http.scaladsl.model.headers.Host.apply(host, port);
    }

    public static Host create(Authority authority) {
      return org.apache.pekko.http.scaladsl.model.headers.Host.apply((org.apache.pekko.http.scaladsl.model.Uri.Authority)authority);
    }

    public abstract org.apache.pekko.http.javadsl.model.Host host();
    public abstract int port();
}
