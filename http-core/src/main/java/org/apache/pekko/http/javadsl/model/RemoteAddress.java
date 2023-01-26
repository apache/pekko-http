/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

import org.apache.pekko.http.javadsl.model.headers.HttpEncodingRanges;
import scala.compat.java8.OptionConverters;

public abstract class RemoteAddress {
    public abstract boolean isUnknown();

    public abstract Optional<InetAddress> getAddress();

    /**
     * Returns a port if defined or 0 otherwise.
     */
    public abstract int getPort();

    public static RemoteAddress create(InetAddress address) {
        return org.apache.pekko.http.scaladsl.model.RemoteAddress.apply(address, OptionConverters.toScala(Optional.empty()));
    }
    public static RemoteAddress create(InetSocketAddress address) {
        return org.apache.pekko.http.scaladsl.model.RemoteAddress.apply(address);
    }
    public static RemoteAddress create(byte[] address) {
        return org.apache.pekko.http.scaladsl.model.RemoteAddress.apply(address);
    }
}
