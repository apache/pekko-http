/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Content-Disposition` header.
 *  Specification: http://tools.ietf.org/html/rfc6266
 */
public abstract class ContentDisposition extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract ContentDispositionType dispositionType();
    public abstract java.util.Map<String, String> getParams();

    public static ContentDisposition create(ContentDispositionType dispositionType, java.util.Map<String, String> params) {
        return new org.apache.pekko.http.scaladsl.model.headers.Content$minusDisposition(((org.apache.pekko.http.scaladsl.model.headers.ContentDispositionType) dispositionType), org.apache.pekko.http.impl.util.Util.convertMapToScala(params));
    }
}
