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

import org.apache.pekko.util.ByteString;

/**
 * Model for the `Available-Dictionary` header. Specification:
 * https://www.rfc-editor.org/rfc/rfc9842#section-2.2
 *
 * @since 2.0.0
 */
public abstract class AvailableDictionary extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  /** The SHA-256 hash of the dictionary */
  public abstract ByteString getHash();

  public static AvailableDictionary create(ByteString hash) {
    return new org.apache.pekko.http.scaladsl.model.headers.Available$minusDictionary(hash);
  }
}
