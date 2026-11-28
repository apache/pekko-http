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

package org.apache.pekko.http

import org.apache.pekko
import pekko.http.scaladsl.settings.RoutingSettings

/** Fixed range-selection settings for standalone range responses. A provider never chooses the selected parts. */
final class RangeResponsePolicy private (
    val rangeCountLimit: Int,
    val rangeCoalescingThreshold: Long,
    val preserveRequestOrder: Boolean) {

  def withRangeCountLimit(value: Int): RangeResponsePolicy = {
    require(value > 0, "range count limit must be positive")
    new RangeResponsePolicy(value, rangeCoalescingThreshold, preserveRequestOrder)
  }

  def withRangeCoalescingThreshold(value: Long): RangeResponsePolicy = {
    require(value >= 0L, "range coalescing threshold must be non-negative")
    new RangeResponsePolicy(rangeCountLimit, value, preserveRequestOrder)
  }

  def withPreserveRequestOrder(value: Boolean): RangeResponsePolicy =
    new RangeResponsePolicy(rangeCountLimit, rangeCoalescingThreshold, value)
}

object RangeResponsePolicy {
  // Mirrors the defaults in pekko.http.routing; standalone users need not start a server or create RoutingSettings.
  private val default = new RangeResponsePolicy(16, 80L, false)

  def create(): RangeResponsePolicy = default

  private[http] def fromRoutingSettings(settings: RoutingSettings): RangeResponsePolicy =
    new RangeResponsePolicy(settings.rangeCountLimit, settings.rangeCoalescingThreshold,
      settings.preserveRangeRequestOrder)
}
