/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

public final class HttpEncodings {
    private HttpEncodings() {}

    public static final HttpEncoding CHUNKED = org.apache.pekko.http.scaladsl.model.headers.HttpEncodings.chunked();
    public static final HttpEncoding COMPRESS = org.apache.pekko.http.scaladsl.model.headers.HttpEncodings.compress();
    public static final HttpEncoding DEFLATE = org.apache.pekko.http.scaladsl.model.headers.HttpEncodings.deflate();
    public static final HttpEncoding GZIP = org.apache.pekko.http.scaladsl.model.headers.HttpEncodings.gzip();
    public static final HttpEncoding IDENTITY = org.apache.pekko.http.scaladsl.model.headers.HttpEncodings.identity();
    public static final HttpEncoding X_COMPRESS = org.apache.pekko.http.scaladsl.model.headers.HttpEncodings.x$minuscompress();
    public static final HttpEncoding X_ZIP = org.apache.pekko.http.scaladsl.model.headers.HttpEncodings.x$minuszip();
}
