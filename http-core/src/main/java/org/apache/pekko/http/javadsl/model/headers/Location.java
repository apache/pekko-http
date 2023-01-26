/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.Uri;

/**
 *  Model for the `Location` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-7.1.2
 */
public abstract class Location extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract Uri getUri();

    public static Location create(Uri uri) {
        return new org.apache.pekko.http.scaladsl.model.headers.Location(uri.asScala());
    }
    public static Location create(String uri) {
        return create(Uri.create(uri));
    }
}
