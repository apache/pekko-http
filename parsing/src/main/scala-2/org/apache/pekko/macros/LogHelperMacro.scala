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

import scala.reflect.macros.blackbox

import org.apache.pekko.annotation.InternalApi

/** INTERNAL API */
@InternalApi
private[pekko] trait LogHelperMacro {
  def debug(msg: String): Unit = macro LogHelperMacro.debugMacro
  def info(msg: String): Unit = macro LogHelperMacro.infoMacro
  def warning(msg: String): Unit = macro LogHelperMacro.warningMacro
}

/** INTERNAL API */
@InternalApi
private[pekko] object LogHelperMacro {
  type LoggerContext = blackbox.Context { type PrefixType = LogHelper }

  def debugMacro(ctx: LoggerContext)(msg: ctx.Expr[String]): ctx.Expr[Unit] =
    ctx.universe.reify {
      {
        val logHelper = ctx.prefix.splice
        if (logHelper.isDebugEnabled)
          logHelper.log.debug(logHelper.prefixString + msg.splice)
      }
    }
  def infoMacro(ctx: LoggerContext)(msg: ctx.Expr[String]): ctx.Expr[Unit] =
    ctx.universe.reify {
      {
        val logHelper = ctx.prefix.splice
        if (logHelper.isInfoEnabled)
          logHelper.log.info(logHelper.prefixString + msg.splice)
      }
    }
  def warningMacro(ctx: LoggerContext)(msg: ctx.Expr[String]): ctx.Expr[Unit] =
    ctx.universe.reify {
      {
        val logHelper = ctx.prefix.splice
        if (logHelper.isWarningEnabled)
          logHelper.log.warning(logHelper.prefixString + msg.splice)
      }
    }
}
