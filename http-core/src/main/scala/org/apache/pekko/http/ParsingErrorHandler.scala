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

import java.util.Optional

import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.event.LoggingAdapter
import pekko.http.javadsl.{ model => jm }
import pekko.http.scaladsl.model.{ ErrorInfo, HttpMethod, HttpProtocol, HttpResponse, StatusCode }
import pekko.http.scaladsl.settings.ServerSettings

/**
 * What is known about a request that failed to parse, at the point where parsing gave up.
 *
 * Every field is optional because a request can be rejected before that part of it has been read:
 * a request with an unsupported method fails before the request target is seen, and one with an
 * unparsable request target fails before the protocol is seen.
 *
 * Note that `rawRequestTarget` is unvalidated, attacker-controlled input, by definition malformed
 * whenever the rejection was caused by the request target itself. Anything that logs or echoes it
 * has to escape it.
 *
 * @since 2.0.0
 */
final class IllegalRequestContext private[http] (
    val method: Option[HttpMethod],
    val rawRequestTarget: Option[String],
    val protocol: Option[HttpProtocol]) {

  /**
   * Java API
   *
   * @since 2.0.0
   */
  def getMethod: Optional[jm.HttpMethod] = method.map(m => m: jm.HttpMethod).toJava

  /**
   * Java API
   *
   * @since 2.0.0
   */
  def getRawRequestTarget: Optional[String] = rawRequestTarget.toJava

  /**
   * Java API
   *
   * @since 2.0.0
   */
  def getProtocol: Optional[jm.HttpProtocol] = protocol.map(p => p: jm.HttpProtocol).toJava

  override def toString: String =
    s"IllegalRequestContext(${method.map(_.value).getOrElse("-")}," +
    s"${rawRequestTarget.getOrElse("-")},${protocol.map(_.value).getOrElse("-")})"
}

object IllegalRequestContext {

  /**
   * A context that knows nothing about the request, used when no information could be recovered.
   *
   * @since 2.0.0
   */
  val empty: IllegalRequestContext = new IllegalRequestContext(None, None, None)

  private[http] def apply(
      method: Option[HttpMethod],
      rawRequestTarget: Option[String],
      protocol: Option[HttpProtocol]): IllegalRequestContext =
    if (method.isEmpty && rawRequestTarget.isEmpty && protocol.isEmpty) empty
    else new IllegalRequestContext(method, rawRequestTarget, protocol)
}

/**
 * Produces the response to a request that failed to parse. Selected by the
 * `pekko.http.server.parsing-error-handler` setting.
 *
 * This is also the earliest public symbol that observes a rejected request, so it is what
 * observability tooling attaches to: the OpenTelemetry Java agent instruments `handle` to emit a
 * span for a request that never reaches the route handler
 * (open-telemetry/opentelemetry-java-instrumentation#5139).
 */
abstract class ParsingErrorHandler {
  def handle(status: StatusCode, error: ErrorInfo, log: LoggingAdapter, settings: ServerSettings): jm.HttpResponse

  /**
   * Called by the server for a request that failed to parse, with what is known about that request.
   *
   * The default implementation ignores `context` and delegates to the four-argument `handle`, so
   * existing handlers keep working unchanged; override this method instead to make use of the
   * context. The parameter is still worth passing for a handler that does not read it, because the
   * arguments of this method are visible to anything instrumenting it: the OpenTelemetry Java agent
   * has to name the span it emits for a rejected request `HTTP` and report neither
   * `http.request.method` nor `url.path`, since the four-argument signature describes only the
   * failure and never the request that caused it. See
   * [[https://github.com/apache/pekko-http/issues/1245]].
   *
   * Note that `DefaultParsingErrorHandler` deliberately keeps implementing the four-argument method
   * rather than this one, so that advice matching that signature keeps firing.
   *
   * @since 2.0.0
   */
  def handle(status: StatusCode, error: ErrorInfo, log: LoggingAdapter, settings: ServerSettings,
      context: IllegalRequestContext): jm.HttpResponse =
    handle(status, error, log, settings)
}

object DefaultParsingErrorHandler extends ParsingErrorHandler {
  import pekko.http.impl.engine.parsing.logParsingError

  // implements the four-argument method on purpose, see the scaladoc of the five-argument one
  override def handle(
      status: StatusCode, info: ErrorInfo, log: LoggingAdapter, settings: ServerSettings): HttpResponse = {
    logParsingError(
      info.withSummaryPrepended(s"Illegal request, responding with status '$status'"),
      log, settings.parserSettings.errorLoggingVerbosity)
    val msg = if (settings.verboseErrorMessages) info.formatPretty else info.summary
    HttpResponse(status, entity = msg)
  }
}
