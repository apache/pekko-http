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

package org.apache.pekko.http.scaladsl.server
package directives

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.http.scaladsl.util.FastFuture
import pekko.http.scaladsl.util.FastFuture._

/**
 * @groupname execution Execution directives
 * @groupprio execution 60
 */
trait ExecutionDirectives {
  import BasicDirectives._

  /**
   * Transforms exceptions thrown during evaluation of its inner route using the given
   * [[pekko.http.scaladsl.server.ExceptionHandler]].
   *
   * @group execution
   */
  def handleExceptions(handler: ExceptionHandler): Directive0 =
    Directive { innerRouteBuilder => ctx =>
      import ctx.executionContext
      def handleException: PartialFunction[Throwable, Future[RouteResult]] =
        handler.andThen(_(ctx.withAcceptAll))
      try innerRouteBuilder(())(ctx).fast.recoverWith(handleException)
      catch {
        case NonFatal(e) => handleException.applyOrElse[Throwable, Future[RouteResult]](e, throw _)
      }
    }

  /**
   * Transforms rejections produced by its inner route using the given
   * [[pekko.http.scaladsl.server.RejectionHandler]].
   *
   * @group execution
   */
  def handleRejections(handler: RejectionHandler): Directive0 =
    extractRequestContext.flatMap { ctx =>
      val maxIterations = 8
      // allow for up to `maxIterations` nested rejections from RejectionHandler before bailing out
      def handle(rejections: immutable.Seq[Rejection], originalRejections: immutable.Seq[Rejection],
          iterationsLeft: Int = maxIterations): Future[RouteResult] =
        if (iterationsLeft > 0) {
          handler(rejections) match {
            case Some(route) =>
              recoverRejectionsWith(handle(_, originalRejections, iterationsLeft - 1))(route)(ctx.withAcceptAll)
            case None => FastFuture.successful(RouteResult.Rejected(rejections))
          }
        } else
          sys.error(s"Rejection handler still produced new rejections after $maxIterations iterations. " +
            s"Is there an infinite handler cycle? Initial rejections: $originalRejections final rejections: $rejections")

      recoverRejectionsWith { rejections =>
        val transformed = RejectionHandler.applyTransformations(rejections)
        handle(transformed, transformed)
      }
    }
}

object ExecutionDirectives extends ExecutionDirectives
