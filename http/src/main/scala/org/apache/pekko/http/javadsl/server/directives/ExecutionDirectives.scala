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

package org.apache.pekko.http.javadsl.server.directives

import org.apache.pekko
import pekko.http.javadsl.server.ExceptionHandler
import pekko.http.javadsl.server.RejectionHandler
import pekko.http.javadsl.server.Route
import pekko.http.scaladsl.server.directives.{ ExecutionDirectives => D }

abstract class ExecutionDirectives extends DebuggingDirectives {

  /**
   * Transforms exceptions thrown during evaluation of its inner route using the given
   * [[pekko.http.javadsl.server.ExceptionHandler]].
   */
  def handleExceptions(handler: ExceptionHandler, inner: java.util.function.Supplier[Route]) = RouteAdapter(
    D.handleExceptions(handler.asScala) {
      inner.get.delegate
    })

  /**
   * Transforms rejections produced by its inner route using the given
   * [[pekko.http.scaladsl.server.RejectionHandler]].
   */
  def handleRejections(handler: RejectionHandler, inner: java.util.function.Supplier[Route]) = RouteAdapter(
    D.handleRejections(handler.asScala) {
      inner.get.delegate
    })

}
