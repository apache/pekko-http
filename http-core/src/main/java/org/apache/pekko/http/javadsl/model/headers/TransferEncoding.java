/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Transfer-Encoding` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-26#section-3.3.1
 */
public abstract class TransferEncoding extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract Iterable<org.apache.pekko.http.javadsl.model.TransferEncoding> getEncodings();

    public static TransferEncoding create(org.apache.pekko.http.javadsl.model.TransferEncoding... encodings) {
        return new org.apache.pekko.http.scaladsl.model.headers.Transfer$minusEncoding(org.apache.pekko.http.impl.util.Util.<org.apache.pekko.http.javadsl.model.TransferEncoding, org.apache.pekko.http.scaladsl.model.TransferEncoding>convertArray(encodings));
    }
}
