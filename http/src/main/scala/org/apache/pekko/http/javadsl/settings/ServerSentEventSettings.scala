/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.settings

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, DoNotInherit }
import pekko.http.impl.settings.ServerSentEventSettingsImpl

/**
 * Public API but not intended for subclassing
 *
 * Options that are in "preview" or "early access" mode.
 * These options may change and/or be removed within patch releases
 * without early notice (e.g. by moving them into a stable supported place).
 */
@ApiMayChange @DoNotInherit
abstract class ServerSentEventSettings private[pekko] () { self: ServerSentEventSettingsImpl =>

  /**
   * The maximum size for parsing server-sent events
   */
  def maxEventSize: Int

  /**
   * The maximum size for parsing lines of a server-sent event
   */
  def maxLineSize: Int

  /**
   * Should events with empty data field be passed to the application.
   */
  def emitEmptyEvents: Boolean

  // ---

  def withMaxEventSize(newValue: Int): ServerSentEventSettings = self.copy(maxEventSize = newValue)
  def withLineLength(newValue: Int): ServerSentEventSettings = self.copy(maxLineSize = newValue)
  def withEmitEmptyEvents(newValue: Boolean): ServerSentEventSettings = self.copy(emitEmptyEvents = newValue)
}
