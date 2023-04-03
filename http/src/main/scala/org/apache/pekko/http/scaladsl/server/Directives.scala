/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server

import org.apache.pekko
import directives._

/**
 * Collects all default directives into one trait for simple importing.
 *
 * See [[pekko.http.javadsl.server.AllDirectives]] for JavaDSL equivalent of this trait.
 */
trait Directives extends RouteConcatenation
    with BasicDirectives
    with CacheConditionDirectives
    with CookieDirectives
    with DebuggingDirectives
    with CodingDirectives
    with ExecutionDirectives
    with FileAndResourceDirectives
    with FileUploadDirectives
    with FormFieldDirectives
    with FutureDirectives
    with HeaderDirectives
    with HostDirectives
    with MarshallingDirectives
    with MethodDirectives
    with MiscDirectives
    with ParameterDirectives
    with TimeoutDirectives
    with PathDirectives
    with RangeDirectives
    with RespondWithDirectives
    with RouteDirectives
    with SchemeDirectives
    with SecurityDirectives
    with WebSocketDirectives
    with FramedEntityStreamingDirectives
    with AttributeDirectives

/**
 * Collects all default directives into one object for simple importing.
 *
 * See [[pekko.http.javadsl.server.Directives]] for JavaDSL equivalent of this trait.
 */
object Directives extends Directives
