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
import pekko.http.impl.util.JavaDurationConverter
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

  def getOutgoingControlFrameBufferSize: Int = outgoingControlFrameBufferSize
  def withOutgoingControlFrameBufferSize(newValue: Int): Http2ServerSettings

  def logFrames: Boolean
  def withLogFrames(shouldLog: Boolean): Http2ServerSettings

  def getPingInterval: Duration = Duration.ofMillis(pingInterval.toMillis)
  def withPingInterval(interval: Duration): Http2ServerSettings = withPingInterval(interval.toMillis.millis)

  def getPingTimeout: Duration = Duration.ofMillis(pingTimeout.toMillis)
  def withPingTimeout(timeout: Duration): Http2ServerSettings = withPingTimeout(timeout.toMillis.millis)

  /**
   * The maximum time a connection is kept open before the server closes it gracefully. When the age of a
   * connection exceeds this value, the server sends a GOAWAY frame, lets requests that are already in flight
   * complete within [[getMaxConnectionAgeGrace]], and then closes the connection. A termination of the
   * connection that is already in progress when the age expires (for example because the server binding is
   * being terminated) keeps its own deadline. The value `ChronoUnit.FOREVER.getDuration` represents an
   * infinite age, which disables this mechanism and is the default.
   *
   * @since 2.0.0
   */
  def getMaxConnectionAge: Duration = JavaDurationConverter.toJava(maxConnectionAge)

  /**
   * Pass `ChronoUnit.FOREVER.getDuration` to disable the maximum connection age.
   *
   * @since 2.0.0
   */
  def withMaxConnectionAge(age: Duration): Http2ServerSettings =
    withMaxConnectionAge(JavaDurationConverter.toScala(age))

  /**
   * The time that requests in flight are given to complete after a connection reached the maximum
   * connection age and the GOAWAY frame was sent. When the grace period expires, the connection is closed
   * even if requests are still in flight. The value `ChronoUnit.FOREVER.getDuration` represents an infinite
   * grace period, which disables the limit, so that the connection is closed only once all requests in
   * flight have completed.
   *
   * @since 2.0.0
   */
  def getMaxConnectionAgeGrace: Duration = JavaDurationConverter.toJava(maxConnectionAgeGrace)

  /**
   * Pass `ChronoUnit.FOREVER.getDuration` for an infinite grace period.
   *
   * @since 2.0.0
   */
  def withMaxConnectionAgeGrace(grace: Duration): Http2ServerSettings =
    withMaxConnectionAgeGrace(JavaDurationConverter.toScala(grace))

  /**
   * The jitter applied to the maximum connection age per connection, as a fraction of the configured age:
   * with the default of 0.1 each connection is closed after between 90% and 110% of the configured age, so
   * that connections that were opened together are not all closed at the same time. 0 disables jitter.
   *
   * @since 2.0.0
   */
  def getMaxConnectionAgeJitter: Double = maxConnectionAgeJitter

  /**
   * @since 2.0.0
   */
  def withMaxConnectionAgeJitter(jitter: Double): Http2ServerSettings

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
