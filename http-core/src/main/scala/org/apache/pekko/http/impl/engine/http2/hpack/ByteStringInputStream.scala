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

package org.apache.pekko.http.impl.engine.http2.hpack

import java.io.InputStream

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi
private[http2] object ByteStringInputStream {
  def apply(bs: ByteString): InputStream = bs match {
    case bss: ByteString.ByteStrings =>
      // bss.asInputStream would create a SequenceInputStream which does not support mark/reset
      bss.compact.asInputStream
    case _ =>
      // returns a ByteArrayInputStream (twitter hpack needs mark/reset support)
      bs.asInputStream
  }
}
