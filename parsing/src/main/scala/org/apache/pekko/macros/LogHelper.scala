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

package org.apache.pekko.macros

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Provides access to a LoggingAdapter which each call guarded by `if (log.isXXXEnabled)` to prevent evaluating
 * the message expression eagerly.
 */
@InternalApi
private[pekko] trait LogHelper extends LogHelperMacro {
  def log: pekko.event.LoggingAdapter
  def isDebugEnabled: Boolean = log.isDebugEnabled
  def isInfoEnabled: Boolean = log.isInfoEnabled
  def isWarningEnabled: Boolean = log.isWarningEnabled

  /** Override to prefix every log message with a user-defined context string */
  def prefixString: String = ""
}
