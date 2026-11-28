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

package org.apache.pekko.http.javadsl.model

import java.util.Optional

import scala.jdk.javaapi.CollectionConverters

import org.apache.pekko
import pekko.http.impl.model.SelectedRepresentationConditionEvaluator
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.model.headers.EntityTag

/** Java API for cache validation and range preconditions on a selected GET or HEAD representation. */
object SelectedRepresentationConditions {

  /**
   * Evaluate an eligible selected GET or HEAD representation, as in the full overload, using a
   * modification date that is not presumed to be a strong validator.
   */
  def evaluate(
      method: HttpMethod,
      requestHeaders: java.lang.Iterable[? <: HttpHeader],
      entityTag: Optional[EntityTag],
      lastModified: Optional[DateTime]): SelectedRepresentationDecision =
    evaluate(method, requestHeaders, entityTag, lastModified, strongLastModified = false)

  /**
   * Call only after normal request checks succeed and the original GET or HEAD would otherwise
   * return 200 (OK) for an existing selected representation. Leave redirects and failures from
   * those checks unchanged (RFC 9110 section 13.2.1). An origin may use this helper; a cache must
   * have selected a response it may reuse and applied its own cache-specific rules. Do not
   * evaluate for a forwarding intermediary that cannot serve the representation itself.
   *
   * Pass the original method and the selected representation's validators. An empty Optional
   * means there is no validator of that kind, not that the representation is absent. If-None-Match
   * with a wildcard can match without an ETag. Only set `strongLastModified` when the date is known
   * to be strong (RFC 9110 section 8.8.2.2). The caller owns earlier preconditions (DELEGATE),
   * response metadata, and any range selection or data access.
   */
  def evaluate(
      method: HttpMethod,
      requestHeaders: java.lang.Iterable[? <: HttpHeader],
      entityTag: Optional[EntityTag],
      lastModified: Optional[DateTime],
      strongLastModified: Boolean): SelectedRepresentationDecision =
    SelectedRepresentationConditionEvaluator.evaluate(
      method.asScala, CollectionConverters.asScala(requestHeaders).iterator.map(header => (header: HttpHeader).asScala),
      entityTag.asScala, lastModified.asScala, strongLastModified)
}
