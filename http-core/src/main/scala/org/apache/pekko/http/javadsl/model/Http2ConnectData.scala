/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.javadsl.model

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, DoNotInherit }
import pekko.stream.javadsl.Source
import pekko.util.ByteString

/**
 * Incoming tunnel bytes for an application-handled regular HTTP/2 CONNECT request.
 *
 * @since 2.1.0
 */
@ApiMayChange
@DoNotInherit
trait Http2ConnectData {

  /** Returns the single-use source of incoming tunnel bytes, separate from the request entity. */
  def getData(): Source[ByteString, ?]

  /**
   * Explicitly accept this CONNECT stream with status 200 and send the supplied source as tunnel bytes.
   * Response headers can be added. Changing the final status to a non-2xx response rejects the tunnel.
   */
  def accept(outgoing: Source[ByteString, ?]): HttpResponse
}
