/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.settings

import java.time.Duration

import org.apache.pekko.http.scaladsl

import scala.concurrent.duration.DurationLong

trait Http2ClientSettings { self: scaladsl.settings.Http2ClientSettings.Http2ClientSettingsImpl =>
  def requestEntityChunkSize: Int
  def withRequestEntityChunkSize(newValue: Int): Http2ClientSettings = self.copy(requestEntityChunkSize = newValue)

  def incomingConnectionLevelBufferSize: Int
  def withIncomingConnectionLevelBufferSize(newValue: Int): Http2ClientSettings =
    self.copy(incomingConnectionLevelBufferSize = newValue)

  def incomingStreamLevelBufferSize: Int
  def withIncomingStreamLevelBufferSize(newValue: Int): Http2ClientSettings =
    copy(incomingStreamLevelBufferSize = newValue)

  def maxConcurrentStreams: Int
  def withMaxConcurrentStreams(newValue: Int): Http2ClientSettings = copy(maxConcurrentStreams = newValue)

  def outgoingControlFrameBufferSize: Int
  def withOutgoingControlFrameBufferSize(newValue: Int): Http2ClientSettings =
    copy(outgoingControlFrameBufferSize = newValue)

  def logFrames: Boolean
  def withLogFrames(shouldLog: Boolean): Http2ClientSettings = copy(logFrames = shouldLog)

  def getPingInterval: Duration = Duration.ofMillis(pingInterval.toMillis)
  def withPingInterval(interval: Duration): Http2ClientSettings = copy(pingInterval = interval.toMillis.millis)

  def getPingTimeout: Duration = Duration.ofMillis(pingTimeout.toMillis)
  def withPingTimeout(timeout: Duration): Http2ClientSettings = copy(pingTimeout = timeout.toMillis.millis)

  def getMaxPersistentAttempts: Int = maxPersistentAttempts
  def withMaxPersistentAttempts(max: Int): Http2ClientSettings = copy(maxPersistentAttempts = max)

  def getCompletionTimeout: Duration = Duration.ofMillis(completionTimeout.toMillis)
  def withCompletionTimeout(timeout: Duration): Http2ClientSettings = copy(completionTimeout = timeout.toMillis.millis)

  def getBaseConnectionBackoff: Duration = Duration.ofMillis(baseConnectionBackoff.toMillis)
  def withBaseConnectionBackoff(backoff: Duration): Http2ClientSettings =
    copy(baseConnectionBackoff = backoff.toMillis.millis)

  def getMaxConnectionBackoff: Duration = Duration.ofMillis(maxConnectionBackoff.toMillis)
  def withMaxConnectionBackoff(backoff: Duration): Http2ClientSettings =
    copy(maxConnectionBackoff = backoff.toMillis.millis)

}
