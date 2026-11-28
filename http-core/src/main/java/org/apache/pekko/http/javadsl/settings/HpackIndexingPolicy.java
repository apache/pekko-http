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

package org.apache.pekko.http.javadsl.settings;

/**
 * Decides whether to skip a new outbound HPACK dynamic table entry.
 *
 * <p>This is a performance policy, not a confidentiality control. Exact indexed matches and
 * never-indexed representations take precedence. Implementations must be non-blocking; an instance
 * supplied to settings can be shared across concurrent connections and must be thread-safe.
 *
 * @since 2.1.0
 */
@FunctionalInterface
public interface HpackIndexingPolicy {
  /**
   * Returns true to encode this otherwise eligible field without inserting it. The field name and
   * value are provided as they will be sent.
   *
   * @since 2.1.0
   */
  boolean skipInsertion(String name, String value);
}
