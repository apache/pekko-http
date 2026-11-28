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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.http.impl.model.SelectedRepresentationConditionEvaluator
import pekko.http.javadsl.model.SelectedRepresentationDecision
import pekko.http.scaladsl.model.headers.EntityTag

/** Cache validation and range preconditions for an already selected GET or HEAD representation. */
object SelectedRepresentationConditions {

  /**
   * Call only when normal request checks have succeeded and the original GET or HEAD would otherwise
   * return 200 (OK) for an existing selected representation. Leave redirects and failures from those
   * checks unchanged (RFC 9110 section 13.2.1).
   *
   * This is for an origin server, or a cache that has selected a response it may reuse and applied
   * its own cache-specific rules. Do not use it to evaluate conditions on behalf of a forwarding
   * intermediary that cannot serve the representation itself.
   *
   * Pass the original method even if HEAD is rendered as GET internally. Supply validators for the
   * selected representation, not another variant or the uncoded source. An absent validator does
   * not mean the representation is absent: If-None-Match with a wildcard can match without an ETag.
   * Set `strongLastModified` only when the date is known to be a strong validator (RFC 9110 section
   * 8.8.2.2).
   *
   * This only evaluates modeled cache validators and the If-Range gate (RFC 9110 sections 13.1.2,
   * 13.1.3, 13.1.5 and 13.2.2). It delegates other methods and earlier origin preconditions; the
   * caller must not translate DELEGATE to an unconditional response. EVALUATE_RANGE does not
   * select slices or imply that the representation supports ranges. The caller remains responsible
   * for response/cache metadata and reading or rendering the representation, if needed.
   */
  def evaluate(
      method: HttpMethod,
      requestHeaders: Iterable[HttpHeader],
      entityTag: Option[EntityTag],
      lastModified: Option[DateTime],
      strongLastModified: Boolean = false): SelectedRepresentationDecision =
    SelectedRepresentationConditionEvaluator.evaluate(method, requestHeaders.iterator, entityTag, lastModified,
      strongLastModified)
}
