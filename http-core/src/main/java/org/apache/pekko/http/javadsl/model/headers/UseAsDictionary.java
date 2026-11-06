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

import java.util.Collections;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Model for the `Use-As-Dictionary` header. Specification:
 * https://www.rfc-editor.org/rfc/rfc9842#section-2.1
 *
 * @since 2.0.0
 */
public abstract class UseAsDictionary extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  /** The URL pattern of the requests that the dictionary can be used for */
  public abstract String getMatchPattern();

  /** The Fetch request destinations that the dictionary can be used for, empty for all of them */
  public abstract Iterable<String> getMatchDest();

  /** An identifier that clients send back in the `Dictionary-ID` header, empty for none */
  public abstract String getId();

  /** The format of the dictionary */
  public abstract String getDictionaryType();

  public static UseAsDictionary create(String matchPattern) {
    return create(matchPattern, Collections.emptyList(), "");
  }

  public static UseAsDictionary create(String matchPattern, Iterable<String> matchDest, String id) {
    return new org.apache.pekko.http.scaladsl.model.headers.Use$minusAs$minusDictionary(
        matchPattern, CollectionConverters.asScala(matchDest).toList(), id, "raw");
  }
}
