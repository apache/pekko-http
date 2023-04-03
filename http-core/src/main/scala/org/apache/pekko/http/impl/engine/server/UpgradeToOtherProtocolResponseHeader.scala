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
