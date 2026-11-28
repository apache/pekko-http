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

package org.apache.pekko.http.impl.server

import org.apache.pekko
import pekko.http.scaladsl.model.ContentRange
import pekko.http.scaladsl.model.headers.ByteRange

/** HTTP byte-range selection shared by the route and the standalone representation planner. */
private[http] object RangeSelection {
  final class IndexRange(val start: Long, val end: Long) {
    def length: Long = end - start
    def distance(other: IndexRange): Long = mergedEnd(other) - mergedStart(other) - (length + other.length)
    def mergeWith(other: IndexRange): IndexRange = new IndexRange(mergedStart(other), mergedEnd(other))
    def contentRange(entityLength: Long): ContentRange.Default = ContentRange(start, end - 1, entityLength)
    private def mergedStart(other: IndexRange): Long = math.min(start, other.start)
    private def mergedEnd(other: IndexRange): Long = math.max(end, other.end)
  }

  def satisfiable(entityLength: Long)(range: ByteRange): Boolean =
    range match {
      case ByteRange.Slice(firstPos, _)   => firstPos < entityLength
      case ByteRange.FromOffset(firstPos) => firstPos < entityLength
      case ByteRange.Suffix(length)       => length > 0
    }

  def indexRange(entityLength: Long)(range: ByteRange): IndexRange =
    range match {
      case ByteRange.Slice(start, end)    => new IndexRange(start, math.min(end + 1, entityLength))
      case ByteRange.FromOffset(first)    => new IndexRange(first, entityLength)
      case ByteRange.Suffix(suffixLength) => new IndexRange(math.max(0, entityLength - suffixLength), entityLength)
    }

  // See the range-coalescing-threshold setting in reference.conf for the rationale.
  def coalesceRanges(iRanges: Seq[IndexRange], threshold: Long): Seq[IndexRange] =
    iRanges.foldLeft(Seq.empty[IndexRange]) { (acc, iRange) =>
      val (mergeCandidates, otherCandidates) = acc.partition(_.distance(iRange) <= threshold)
      val merged = mergeCandidates.foldLeft(iRange)(_.mergeWith(_))
      // Merging into the current range keeps the surviving ranges in request order.
      otherCandidates :+ merged
    }
}
