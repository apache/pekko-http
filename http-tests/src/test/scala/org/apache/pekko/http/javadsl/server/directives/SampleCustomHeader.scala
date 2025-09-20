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

import scala.util.{ Success, Try }

import org.apache.pekko.http.scaladsl.model.headers.{ ModeledCustomHeader, ModeledCustomHeaderCompanion }

// no support for modeled headers in the Java DSL yet, so this has to live here

object SampleCustomHeader extends ModeledCustomHeaderCompanion[SampleCustomHeader] {
  override def name: String = "X-Sample-Custom-Header"
  override def parse(value: String): Try[SampleCustomHeader] = Success(new SampleCustomHeader(value))
}

class SampleCustomHeader(val value: String) extends ModeledCustomHeader[SampleCustomHeader] {
  override def companion: ModeledCustomHeaderCompanion[SampleCustomHeader] = SampleCustomHeader
  override def renderInResponses(): Boolean = true
  override def renderInRequests(): Boolean = true
}
