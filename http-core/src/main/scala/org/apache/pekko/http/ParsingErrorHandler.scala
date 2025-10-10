/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http

import org.apache.pekko
import pekko.event.LoggingAdapter
import pekko.http.javadsl.{ model => jm }
import pekko.http.scaladsl.model.{ ErrorInfo, HttpResponse, StatusCode }
import pekko.http.scaladsl.settings.ServerSettings

abstract class ParsingErrorHandler {
  def handle(status: StatusCode, error: ErrorInfo, log: LoggingAdapter, settings: ServerSettings): jm.HttpResponse
}

object DefaultParsingErrorHandler extends ParsingErrorHandler {
  import pekko.http.impl.engine.parsing.logParsingError

  override def handle(
      status: StatusCode, info: ErrorInfo, log: LoggingAdapter, settings: ServerSettings): HttpResponse = {
    logParsingError(
      info.withSummaryPrepended(s"Illegal request, responding with status '$status'"),
      log, settings.parserSettings.errorLoggingVerbosity)
    val msg = if (settings.verboseErrorMessages) info.formatPretty else info.summary
    HttpResponse(status, entity = msg)
  }
}
