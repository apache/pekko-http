/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.macros

import org.apache.pekko.annotation.InternalApi

import scala.quoted._

/** INTERNAL API */
@InternalApi
private[pekko] trait LogHelperMacro { self: LogHelper =>
  inline def debug(inline msg: String): Unit =
    ${ LogHelperMacro.guard('{ isDebugEnabled }, '{ log.debug(prefixString + msg) }) }
  inline def info(inline msg: String): Unit =
    ${ LogHelperMacro.guard('{ isInfoEnabled }, '{ log.info(prefixString + msg) }) }
  inline def warning(inline msg: String): Unit =
    ${ LogHelperMacro.guard('{ isWarningEnabled }, '{ log.warning(prefixString + msg) }) }
}

/** INTERNAL API */
@InternalApi
private[pekko] object LogHelperMacro {
  def guard(isEnabled: Expr[Boolean], log: Expr[Unit])(using Quotes): Expr[Unit] = '{ if ($isEnabled) $log }
}
