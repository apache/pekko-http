/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.util

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.ccompat._
import pekko.util.ByteString

/**
 * INTERNAL API
 */
@InternalApi
private[http] class EnhancedByteStringTraversableOnce(val byteStrings: IterableOnce[ByteString]) extends AnyVal {
  def join: ByteString = byteStrings.foldLeft(ByteString.empty)(_ ++ _)
}
