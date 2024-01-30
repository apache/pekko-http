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

import java.io.{ ByteArrayInputStream, InputStream }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString
import pekko.util.ByteString.ByteString1C

/** INTERNAL API */
@InternalApi
private[http2] object ByteStringInputStream {

  def apply(bs: ByteString): InputStream =
    bs match {
      case cs: ByteString1C =>
        // TODO optimise, ByteString needs to expose InputStream (esp if array backed, nice!)
        new ByteArrayInputStream(cs.toArrayUnsafe())
      case _ =>
        // NOTE: We actually measured recently, and compact + use array was pretty good usually
        apply(bs.compact)
    }
}
