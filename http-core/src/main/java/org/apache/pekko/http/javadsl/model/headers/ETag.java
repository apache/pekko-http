/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `ETag` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26#section-2.3
 */
public abstract class ETag extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract EntityTag etag();

    public static ETag create(EntityTag etag) {
        return new org.apache.pekko.http.scaladsl.model.headers.ETag(((org.apache.pekko.http.scaladsl.model.headers.EntityTag) etag));
    }
}
