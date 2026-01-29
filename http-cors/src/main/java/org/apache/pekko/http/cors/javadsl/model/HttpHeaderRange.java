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

package org.apache.pekko.http.cors.javadsl.model;

import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.cors.scaladsl.model.HttpHeaderRange$;

/** @see HttpHeaderRanges for convenience access to often used values. */
public abstract class HttpHeaderRange {
  public abstract boolean matches(String header);

  /**
   * Produces a new range that matches the headers of this range and the given range.
   *
   * @since 2.0.0
   */
  public abstract HttpHeaderRange concat(HttpHeaderRange range);

  public static HttpHeaderRange create(String... headers) {
    return HttpHeaderRange$.MODULE$.apply(Util.convertArray(headers));
  }
}
