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

package org.apache.pekko.http.javadsl.settings

import java.time.Duration

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.ccompat.JavaConverters._
import pekko.http.scaladsl
import com.typesafe.config.Config

import scala.concurrent.duration.DurationLong

@DoNotInherit
trait Http2ServerSettings {
  self: scaladsl.settings.Http2ServerSettings
    with pekko.http.scaladsl.settings.Http2ServerSettings.Http2ServerSettingsImpl =>
  def getRequestEntityChunkSize: Int = requestEntityChunkSize
  def withRequestEntityChunkSize(newRequestEntityChunkSize: Int): Http2ServerSettings

  def getIncomingConnectionLevelBufferSize: Int = incomingConnectionLevelBufferSize
  def withIncomingConnectionLevelBufferSize(newIncomingConnectionLevelBufferSize: Int): Http2ServerSettings

  def getIncomingStreamLevelBufferSize: Int = incomingStreamLevelBufferSize
  def withIncomingStreamLevelBufferSize(newIncomingStreamLevelBufferSize: Int): Http2ServerSettings

  def minCollectStrictEntitySize: Int
  def withMinCollectStrictEntitySize(newValue: Int): Http2ServerSettings

  def getMaxConcurrentStreams: Int = maxConcurrentStreams
  def withMaxConcurrentStreams(newValue: Int): Http2ServerSettings

  def getOutgoingControlFrameBufferSize: Int = outgoingControlFrameBufferSize
  def withOutgoingControlFrameBufferSize(newValue: Int): Http2ServerSettings

  def logFrames: Boolean
  def withLogFrames(shouldLog: Boolean): Http2ServerSettings

  def getPingInterval: Duration = Duration.ofMillis(pingInterval.toMillis)
  def withPingInterval(interval: Duration): Http2ServerSettings = withPingInterval(interval.toMillis.millis)

  def getPingTimeout: Duration = Duration.ofMillis(pingTimeout.toMillis)
  def withPingTimeout(timeout: Duration): Http2ServerSettings = withPingTimeout(timeout.toMillis.millis)

  def getFrameTypeThrottleFrameTypes(): java.util.Set[String] = frameTypeThrottleFrameTypes.asJava
  def getFrameTypeThrottleCost(): Int = frameTypeThrottleCost
  def getFrameTypeThrottleBurst(): Int = frameTypeThrottleBurst
  def getFrameTypeThrottleInterval: Duration = Duration.ofMillis(frameTypeThrottleInterval.toMillis)

  def withFrameTypeThrottleInterval(interval: Duration): Http2ServerSettings =
    withFrameTypeThrottleInterval(interval.toMillis.millis)
}
object Http2ServerSettings extends SettingsCompanion[Http2ServerSettings] {
  def create(config: Config): Http2ServerSettings = scaladsl.settings.Http2ServerSettings(config)
  def create(configOverrides: String): Http2ServerSettings = scaladsl.settings.Http2ServerSettings(configOverrides)
}
