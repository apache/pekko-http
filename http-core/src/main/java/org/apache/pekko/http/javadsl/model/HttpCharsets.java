/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.scaladsl.model.HttpCharsets$;

import java.util.Optional;

/**
 * Contains a set of predefined charsets.
 */
public final class HttpCharsets {
    private HttpCharsets() {}

    public static final HttpCharset US_ASCII = org.apache.pekko.http.scaladsl.model.HttpCharsets.US$minusASCII();
    public static final HttpCharset ISO_8859_1 = org.apache.pekko.http.scaladsl.model.HttpCharsets.ISO$minus8859$minus1();
    public static final HttpCharset UTF_8 = org.apache.pekko.http.scaladsl.model.HttpCharsets.UTF$minus8();
    public static final HttpCharset UTF_16 = org.apache.pekko.http.scaladsl.model.HttpCharsets.UTF$minus16();
    public static final HttpCharset UTF_16BE = org.apache.pekko.http.scaladsl.model.HttpCharsets.UTF$minus16BE();
    public static final HttpCharset UTF_16LE = org.apache.pekko.http.scaladsl.model.HttpCharsets.UTF$minus16LE();

    /**
     * Create and return a custom charset.
     */
    public static HttpCharset custom(String value, String... aliases) {
        return org.apache.pekko.http.scaladsl.model.HttpCharset.custom(value, Util.<String, String>convertArray(aliases));
    }

    /**
     * Returns Some(charset) if the charset with the given name was found and None otherwise.
     */
    public static Optional<HttpCharset> lookup(String name) {
        return Util.<HttpCharset, org.apache.pekko.http.scaladsl.model.HttpCharset>lookupInRegistry(HttpCharsets$.MODULE$, name);
    }
}
