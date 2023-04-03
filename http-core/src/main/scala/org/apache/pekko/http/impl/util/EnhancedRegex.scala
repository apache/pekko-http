/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.util

import org.apache.pekko.annotation.InternalApi

import scala.util.matching.Regex

/**
 * INTERNAL API
 */
@InternalApi
private[http] class EnhancedRegex(val regex: Regex) extends AnyVal {
  def groupCount = regex.pattern.matcher("").groupCount()
}
