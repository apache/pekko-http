/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.server

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.scaladsl.model.headers.CustomHeader
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

/**
 * Internal API
 */
@InternalApi
private[http] final case class UpgradeToOtherProtocolResponseHeader(handler: Flow[ByteString, ByteString, Any])
    extends InternalCustomHeader("UpgradeToOtherProtocolResponseHeader")

/**
 * Internal API
 */
@InternalApi
private[http] abstract class InternalCustomHeader(val name: String) extends CustomHeader {
  final def renderInRequests = false
  final def renderInResponses = false
  def value: String = ""
}
