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
 * The {@code Trailer} header lists fields that the sender expects to include in the trailer
 * section. See <a href="https://www.rfc-editor.org/rfc/rfc9110.html#section-6.6.2">RFC 9110,
 * Section 6.6.2</a>.
 *
 * @since 1.3.0
 */
public abstract class Trailer extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  /**
   * @return an iterable collection of field names that are expected to be present in the trailer
   */
  public abstract Iterable<String> getTrailers();

  /**
   * Creates a new `Trailer` header with the specified trailer field names.
   *
   * @param values the names of the fields that are expected to be present in the trailer
   * @return a new `Trailer` header instance
   */
  public static Trailer create(String... values) {
    return org.apache.pekko.http.scaladsl.model.headers.Trailer.apply(Util.convertArray(values));
  }
}
