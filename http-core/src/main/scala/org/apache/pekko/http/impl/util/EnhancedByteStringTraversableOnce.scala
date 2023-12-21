/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.util

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString

/**
 * INTERNAL API
 */
@InternalApi
private[http] class EnhancedByteStringTraversableOnce(val byteStrings: IterableOnce[ByteString]) extends AnyVal {
  def join: ByteString = byteStrings.foldLeft(ByteString.empty)(_ ++ _)
}
