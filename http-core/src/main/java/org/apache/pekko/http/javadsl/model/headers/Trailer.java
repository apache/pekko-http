/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.impl.util.Util;

/**
 * The `Trailer` header is used before a message body to indicate which fields will be present
 * in the trailers when using chunked transfer encoding.
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-4.4">RFC 7230, Section 4.4</a>
 *
 * @since 1.3.0
 */
public abstract class Trailer extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    /**
     * Returns the names of the trailer fields that are ex
     *
     * @return an iterable collection of trailer field names
     */
    public abstract Iterable<String> getTrailers();

    /**
     * Creates a new `Trailer` header with the specified trailer field names.
     *
     * @param values the names of the fields that are
     * @return a new `Trailer` header instance
     */
    public static Trailer create(String... values) {
        return org.apache.pekko.http.scaladsl.model.headers.Trailer.apply(Util.convertArray(values));
    }
}
