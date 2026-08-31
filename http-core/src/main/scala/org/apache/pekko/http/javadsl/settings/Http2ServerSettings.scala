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
import pekko.http.scaladsl
import com.typesafe.config.Config

import scala.concurrent.duration.DurationLong
import scala.jdk.CollectionConverters._

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

  /**
   * The maximum size of a decoded header list that this endpoint is prepared to accept, in bytes. The value is
   * advertised to the peer via SETTINGS_MAX_HEADER_LIST_SIZE and the same limit is applied to the accumulated
   * header block fragments of a HEADERS frame and its CONTINUATION frames.
   *
   * @since 2.0.0
   */
  def getMaxHeaderListSize: Int = maxHeaderListSize

  /**
   * @since 2.0.0
   */
  def withMaxHeaderListSize(newValue: Int): Http2ServerSettings

  /**
   * The largest frame payload this endpoint accepts, in bytes. A larger incoming frame is rejected with a
   * FRAME_SIZE_ERROR on its frame header, before the payload is buffered. No larger SETTINGS_MAX_FRAME_SIZE is
   * advertised, so a peer that follows the spec keeps to the 16 KiB default and the extra room is leniency for peers
   * that do not. RFC 9113, section 4.2 constrains it to be between 16 KiB and 16 MiB - 1.
   *
   * @since 2.0.0
   */
  def getMaxFrameSize: Int = maxFrameSize

  /**
   * @since 2.0.0
   */
  def withMaxFrameSize(newValue: Int): Http2ServerSettings

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
