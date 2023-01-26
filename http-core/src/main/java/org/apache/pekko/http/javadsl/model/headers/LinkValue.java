/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.Uri;
import org.apache.pekko.http.impl.util.Util;

public abstract class LinkValue {
    public abstract Uri getUri();
    public abstract Iterable<LinkParam> getParams();

    public static LinkValue create(Uri uri, LinkParam... params) {
        return new org.apache.pekko.http.scaladsl.model.headers.LinkValue(
                uri.asScala(),
                Util.<LinkParam, org.apache.pekko.http.scaladsl.model.headers.LinkParam>convertArray(params));
    }
}
