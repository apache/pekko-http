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

package org.apache.pekko.http.impl.util

import com.typesafe.config.Config

import scala.concurrent.duration.{ Duration, FiniteDuration }
import org.apache.pekko
import pekko.ConfigurationException
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[http] class EnhancedConfig(val underlying: Config) extends AnyVal {

  def getPotentiallyInfiniteDuration(path: String): Duration = underlying.getString(path) match {
    case "infinite" => Duration.Inf
    case x          => Duration(x)
  }

  def getFiniteDuration(path: String): FiniteDuration = Duration(underlying.getString(path)) match {
    case x: FiniteDuration => x
    case _                 => throw new ConfigurationException(s"Config setting '$path' must be a finite duration")
  }

  def getPossiblyInfiniteInt(path: String): Int = underlying.getString(path) match {
    case "infinite" => Int.MaxValue
    case x          => underlying.getInt(path)
  }

  def getIntBytes(path: String): Int = {
    val value: Long = underlying.getBytes(path)
    if (value <= Int.MaxValue) value.toInt
    else throw new ConfigurationException(s"Config setting '$path' must not be larger than ${Int.MaxValue}")
  }

  def getPossiblyInfiniteIntBytes(path: String): Int = underlying.getString(path) match {
    case "infinite" => Int.MaxValue
    case x          => getIntBytes(path)
  }

  def getPossiblyInfiniteBytes(path: String): Long = underlying.getString(path) match {
    case "infinite" => Long.MaxValue
    case x          => underlying.getBytes(path)
  }
  def ifDefined[T](path: String, f: (Config, String) => T): Option[T] =
    if (underlying.hasPath(path)) Some(f(underlying, path))
    else None
}
