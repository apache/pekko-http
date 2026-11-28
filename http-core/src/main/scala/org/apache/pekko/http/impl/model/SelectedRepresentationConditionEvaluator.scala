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

package org.apache.pekko.http.impl.model

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.javadsl.model.SelectedRepresentationDecision
import pekko.http.javadsl.model.SelectedRepresentationDecision._
import pekko.http.scaladsl.model.{ DateTime, HttpHeader, HttpMethod, HttpMethods }
import pekko.http.scaladsl.model.headers.{
  EntityTag,
  EntityTagRange,
  `If-Modified-Since`,
  `If-None-Match`,
  `If-Range`,
  Range
}

/** INTERNAL API. Shared implementation for the Scala and Java model entry points. */
@InternalApi
private[http] object SelectedRepresentationConditionEvaluator {
  def evaluate(
      method: HttpMethod,
      requestHeaders: Iterator[HttpHeader],
      entityTag: Option[EntityTag],
      lastModified: Option[DateTime],
      strongLastModified: Boolean): SelectedRepresentationDecision = {
    if (method != HttpMethods.GET && method != HttpMethods.HEAD) return DELEGATE

    var hasIfNoneMatch = false
    var matchesIfNoneMatch = false
    var modifiedSince = Option.empty[DateTime]
    var delegateModifiedSince = false
    var hasRange = false
    var ignoreRange = false
    var ifRange = Option.empty[Either[EntityTag, DateTime]]

    while (requestHeaders.hasNext) {
      requestHeaders.next() match {
        case `If-None-Match`(range) =>
          hasIfNoneMatch = true
          // The representation has already been selected, even when it does not publish a tag.
          matchesIfNoneMatch = matchesIfNoneMatch || range == EntityTagRange.`*` ||
            entityTag.exists(EntityTag.matchesRange(_, range, weakComparison = true))
        case `If-Modified-Since`(date) if modifiedSince.isEmpty                                                => modifiedSince = Some(date)
        case _: Range if !hasRange                                                                             => hasRange = true
        case `If-Range`(validator) if ifRange.isEmpty                                                          => ifRange = Some(validator)
        case header if header.is("if-match") || header.is("if-unmodified-since") || header.is("if-none-match") =>
          return DELEGATE
        case header if header.is("if-modified-since")              => delegateModifiedSince = true
        case header if header.is("range") || header.is("if-range") => ignoreRange = true
        case _                                                     =>
      }
    }

    if (matchesIfNoneMatch) NOT_MODIFIED
    else if (!hasIfNoneMatch && delegateModifiedSince) DELEGATE
    else if (!hasIfNoneMatch && modifiedSince.exists(date => lastModified.exists(_ <= date))) NOT_MODIFIED
    else if (method != HttpMethods.GET || !hasRange || ignoreRange) PROCEED
    else if (ifRange.forall {
        case Left(tag)   => entityTag.exists(EntityTag.matches(_, tag, weakComparison = false))
        case Right(date) => strongLastModified && lastModified.contains(date)
      }) EVALUATE_RANGE
    else PROCEED
  }
}
