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

import org.apache.pekko.annotation.DoNotInherit;
import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.scaladsl.model.headers.Content$minusLanguage$;

/**
 * The language tags of the intended audience for the representation, as defined by RFC 9110 section
 * 8.5.
 *
 * @since 2.1.0
 */
@DoNotInherit
public abstract class ContentLanguage extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  /** Returns the language tags in field order. */
  public abstract Iterable<Language> getLanguages();

  /** Creates a Content-Language header. An empty list is allowed. */
  public static ContentLanguage create(Language... languages) {
    return Content$minusLanguage$.MODULE$.apply(
        Util.<Language, org.apache.pekko.http.scaladsl.model.headers.Language>convertArray(
            languages));
  }
}
