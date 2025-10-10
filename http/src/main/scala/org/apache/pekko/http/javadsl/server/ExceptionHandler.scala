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

package org.apache.pekko.http.javadsl.server

import RoutingJavaMapping._

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.settings.RoutingSettings
import pekko.http.scaladsl.server

object ExceptionHandler {

  /**
   * Creates a new builder DSL for creating an ExceptionHandler
   */
  def newBuilder: ExceptionHandlerBuilder = new ExceptionHandlerBuilder()

  /** INTERNAL API */
  @InternalApi
  def of(pf: PartialFunction[Throwable, Route]) = new ExceptionHandler(server.ExceptionHandler(pf.andThen(_.delegate)))
}

/**
 * Handles exceptions by turning them into routes. You can create an exception handler in Java code like the following example:
 * <pre>
 *     ExceptionHandler myHandler = ExceptionHandler.of (ExceptionHandler.newPFBuilder()
 *         .match(IllegalArgumentException.class, x -> Directives.complete(StatusCodes.BAD_REQUEST))
 *         .build()
 *     ));
 * </pre>
 */
final class ExceptionHandler private (val asScala: server.ExceptionHandler) {

  /**
   * Creates a new [[ExceptionHandler]] which uses the given one as fallback for this one.
   */
  def withFallback(that: ExceptionHandler): ExceptionHandler = new ExceptionHandler(asScala.withFallback(that.asScala))

  /**
   * "Seals" this handler by attaching a default handler as fallback if necessary.
   */
  def seal(settings: RoutingSettings): ExceptionHandler = new ExceptionHandler(asScala.seal(settings.asScala))
}
