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
