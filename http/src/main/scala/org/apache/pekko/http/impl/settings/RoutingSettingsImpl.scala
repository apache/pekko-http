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

package org.apache.pekko.http.impl.settings

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util._
import com.typesafe.config.Config

/** INTERNAL API */
@InternalApi
private[http] final case class RoutingSettingsImpl(
    verboseErrorMessages: Boolean,
    fileGetConditional: Boolean,
    renderVanityFooter: Boolean,
    rangeCountLimit: Int,
    rangeCoalescingThreshold: Long,
    decodeMaxBytesPerChunk: Int,
    decodeMaxSize: Long) extends pekko.http.scaladsl.settings.RoutingSettings {

  override def productPrefix = "RoutingSettings"
}

object RoutingSettingsImpl extends SettingsCompanionImpl[RoutingSettingsImpl]("pekko.http.routing") {
  def fromSubConfig(root: Config, c: Config) = new RoutingSettingsImpl(
    c.getBoolean("verbose-error-messages"),
    c.getBoolean("file-get-conditional"),
    c.getBoolean("render-vanity-footer"),
    c.getInt("range-count-limit"),
    c.getBytes("range-coalescing-threshold"),
    c.getIntBytes("decode-max-bytes-per-chunk"),
    c.getPossiblyInfiniteBytes("decode-max-size"))
}
