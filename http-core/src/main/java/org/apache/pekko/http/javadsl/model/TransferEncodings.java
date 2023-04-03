/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

public final class TransferEncodings {
    private TransferEncodings() {}

    public static final TransferEncoding CHUNKED  = org.apache.pekko.http.scaladsl.model.TransferEncodings.chunked$.MODULE$;
    public static final TransferEncoding COMPRESS = org.apache.pekko.http.scaladsl.model.TransferEncodings.compress$.MODULE$;
    public static final TransferEncoding DEFLATE  = org.apache.pekko.http.scaladsl.model.TransferEncodings.deflate$.MODULE$;
    public static final TransferEncoding GZIP     = org.apache.pekko.http.scaladsl.model.TransferEncodings.gzip$.MODULE$;
    public static final TransferEncoding TRAILERS = org.apache.pekko.http.scaladsl.model.TransferEncodings.trailers$.MODULE$;
}
