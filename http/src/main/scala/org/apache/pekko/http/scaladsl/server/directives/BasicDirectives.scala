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

import java.util.concurrent.TimeoutException

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.collection.immutable
import pekko.event.LoggingAdapter
import pekko.http.scaladsl.model.Uri.Path
import pekko.util.ConstantFun.scalaIdentityFunction
import pekko.stream.Materializer
import pekko.http.scaladsl.settings.{ ParserSettings, RoutingSettings }
import pekko.http.scaladsl.server.util.Tuple
import pekko.http.scaladsl.util.FastFuture
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.util.FastFuture._

import scala.util.control.NonFatal

/**
 * @groupname basic Basic directives
 * @groupprio basic 10
 */
trait BasicDirectives {

  /**
   * @group basic
   */
  def mapInnerRoute(f: Route => Route): Directive0 =
    Directive { inner => f(inner(())) }

  /**
   * @group basic
   */
  def mapRequestContext(f: RequestContext => RequestContext): Directive0 =
    mapInnerRoute { inner => ctx => inner(f(ctx)) }

  /**
   * @group basic
   */
  def mapRequest(f: HttpRequest => HttpRequest): Directive0 =
    mapRequestContext(_.mapRequest(f))

  /**
   * @group basic
   */
  def mapRouteResultFuture(f: Future[RouteResult] => Future[RouteResult]): Directive0 =
    Directive { inner => ctx =>
      // Convert any exceptions that happened in the inner route to failed futures so the handler
      // can handle those as well.
      val innerResult =
        try inner(())(ctx)
        catch { case NonFatal(ex) => FastFuture.failed(ex) }
      f(innerResult)
    }

  /**
   * @group basic
   */
  def mapRouteResult(f: RouteResult => RouteResult): Directive0 =
    Directive { inner => ctx => inner(())(ctx).fast.map(f)(ctx.executionContext) }

  /**
   * @group basic
   */
  def mapRouteResultWith(f: RouteResult => Future[RouteResult]): Directive0 =
    Directive { inner => ctx => inner(())(ctx).fast.flatMap(f)(ctx.executionContext) }

  /**
   * @group basic
   */
  def mapRouteResultPF(f: PartialFunction[RouteResult, RouteResult]): Directive0 =
    mapRouteResult(f.applyOrElse(_, identity[RouteResult]))

  /**
   * @group basic
   */
  def mapRouteResultWithPF(f: PartialFunction[RouteResult, Future[RouteResult]]): Directive0 =
    mapRouteResultWith(f.applyOrElse(_, FastFuture.successful[RouteResult]))

  /**
   * @group basic
   */
  def recoverRejections(f: immutable.Seq[Rejection] => RouteResult): Directive0 =
    mapRouteResultPF { case RouteResult.Rejected(rejections) => f(rejections) }

  /**
   * @group basic
   */
  def recoverRejectionsWith(f: immutable.Seq[Rejection] => Future[RouteResult]): Directive0 =
    mapRouteResultWithPF { case RouteResult.Rejected(rejections) => f(rejections) }

  /**
   * @group basic
   */
  def mapRejections(f: immutable.Seq[Rejection] => immutable.Seq[Rejection]): Directive0 =
    recoverRejections(rejections => RouteResult.Rejected(f(rejections)))

  /**
   * @group basic
   */
  def mapResponse(f: HttpResponse => HttpResponse): Directive0 =
    mapRouteResultPF { case RouteResult.Complete(response) => RouteResult.Complete(f(response)) }

  /**
   * @group basic
   */
  def mapResponseEntity(f: ResponseEntity => ResponseEntity): Directive0 =
    mapResponse(_.mapEntity(f))

  /**
   * @group basic
   */
  def mapResponseHeaders(f: immutable.Seq[HttpHeader] => immutable.Seq[HttpHeader]): Directive0 =
    mapResponse(_.mapHeaders(f))

  /**
   * A Directive0 that always passes the request on to its inner route
   * (i.e. does nothing with the request or the response).
   *
   * @group basic
   */
  def pass: Directive0 = Directive.Empty

  /**
   * Injects the given value into a directive.
   *
   * @group basic
   */
  def provide[T](value: T): Directive1[T] = tprovide(Tuple1(value))

  /**
   * Injects the given values into a directive.
   *
   * @group basic
   */
  def tprovide[L: Tuple](values: L): Directive[L] =
    Directive { _(values) }

  /**
   * Extracts a single value using the given function.
   *
   * @group basic
   */
  def extract[T](f: RequestContext => T): Directive1[T] =
    textract(ctx => Tuple1(f(ctx)))

  /**
   * Extracts a number of values using the given function.
   *
   * @group basic
   */
  def textract[L: Tuple](f: RequestContext => L): Directive[L] =
    Directive { inner => ctx => inner(f(ctx))(ctx) }

  /**
   * Adds a TransformationRejection cancelling all rejections equal to the given one
   * to the list of rejections potentially coming back from the inner route.
   *
   * @group basic
   */
  def cancelRejection(rejection: Rejection): Directive0 =
    cancelRejections(_ == rejection)

  /**
   * Adds a TransformationRejection cancelling all rejections of one of the given classes
   * to the list of rejections potentially coming back from the inner route.
   *
   * @group basic
   */
  def cancelRejections(classes: Class[_]*): Directive0 =
    cancelRejections(r => classes.exists(_.isInstance(r)))

  /**
   * Adds a TransformationRejection cancelling all rejections for which the given filter function returns true
   * to the list of rejections potentially coming back from the inner route.
   *
   * @group basic
   */
  def cancelRejections(cancelFilter: Rejection => Boolean): Directive0 =
    mapRejections(_ :+ TransformationRejection(_.filterNot(cancelFilter)))

  /**
   * Transforms the unmatchedPath of the RequestContext using the given function.
   *
   * @group basic
   */
  def mapUnmatchedPath(f: Uri.Path => Uri.Path): Directive0 =
    mapRequestContext(_.mapUnmatchedPath(f))

  /**
   * Extracts the yet unmatched path from the RequestContext.
   *
   * @group basic
   */
  def extractUnmatchedPath: Directive1[Uri.Path] = BasicDirectives._extractUnmatchedPath

  /**
   * Extracts the already matched path from the RequestContext.
   *
   * @group basic
   */
  def extractMatchedPath: Directive1[Uri.Path] = BasicDirectives._extractMatchedPath

  /**
   * Extracts the current [[HttpRequest]] instance.
   *
   * @group basic
   */
  def extractRequest: Directive1[HttpRequest] = BasicDirectives._extractRequest

  /**
   * Extracts the complete request URI.
   *
   * @group basic
   */
  def extractUri: Directive1[Uri] = BasicDirectives._extractUri

  /**
   * Runs its inner route with the given alternative [[scala.concurrent.ExecutionContextExecutor]].
   *
   * @group basic
   */
  def withExecutionContext(ec: ExecutionContextExecutor): Directive0 =
    mapRequestContext(_.withExecutionContext(ec))

  /**
   * Extracts the [[scala.concurrent.ExecutionContextExecutor]] from the [[pekko.http.scaladsl.server.RequestContext]].
   *
   * @group basic
   */
  def extractExecutionContext: Directive1[ExecutionContextExecutor] = BasicDirectives._extractExecutionContext

  /**
   * Runs its inner route with the given alternative [[pekko.stream.Materializer]].
   *
   * @group basic
   */
  def withMaterializer(materializer: Materializer): Directive0 =
    mapRequestContext(_.withMaterializer(materializer))

  /**
   * Extracts the [[pekko.stream.Materializer]] from the [[pekko.http.scaladsl.server.RequestContext]].
   *
   * @group basic
   */
  def extractMaterializer: Directive1[Materializer] = BasicDirectives._extractMaterializer

  /**
   * Extracts the [[pekko.actor.ActorSystem]] if the available Materializer is an [[pekko.stream.ActorMaterializer]].
   * Otherwise throws an exception as it won't be able to extract the system from arbitrary materializers.
   *
   * @group basic
   */
  def extractActorSystem: Directive1[ActorSystem] = extract { ctx =>
    ctx.materializer.system
  }

  /**
   * Runs its inner route with the given alternative [[pekko.event.LoggingAdapter]].
   *
   * @group basic
   */
  def withLog(log: LoggingAdapter): Directive0 =
    mapRequestContext(_.withLog(log))

  /**
   * Extracts the [[pekko.event.LoggingAdapter]] from the [[pekko.http.scaladsl.server.RequestContext]].
   *
   * @group basic
   */
  def extractLog: Directive1[LoggingAdapter] =
    BasicDirectives._extractLog

  /**
   * Runs its inner route with the given alternative [[RoutingSettings]].
   *
   * @group basic
   */
  def withSettings(settings: RoutingSettings): Directive0 =
    mapRequestContext(_.withRoutingSettings(settings))

  /**
   * Runs the inner route with settings mapped by the given function.
   *
   * @group basic
   */
  def mapSettings(f: RoutingSettings => RoutingSettings): Directive0 =
    mapRequestContext(ctx => ctx.withRoutingSettings(f(ctx.settings)))

  /**
   * Extracts the [[RoutingSettings]] from the [[pekko.http.scaladsl.server.RequestContext]].
   *
   * @group basic
   */
  def extractSettings: Directive1[RoutingSettings] =
    BasicDirectives._extractSettings

  /**
   * Extracts the [[pekko.http.scaladsl.settings.ParserSettings]] from the [[pekko.http.scaladsl.server.RequestContext]].
   *
   * @group basic
   */
  def extractParserSettings: Directive1[ParserSettings] =
    BasicDirectives._extractParserSettings

  /**
   * Extracts the [[pekko.http.scaladsl.server.RequestContext]] itself.
   *
   * @group basic
   */
  def extractRequestContext: Directive1[RequestContext] = BasicDirectives._extractRequestContext

  /**
   * Extracts the [[pekko.http.scaladsl.model.RequestEntity]] from the [[pekko.http.scaladsl.server.RequestContext]].
   *
   * @group basic
   */
  def extractRequestEntity: Directive1[RequestEntity] = BasicDirectives._extractRequestEntity

  /**
   * Extracts the entities `dataBytes` [[pekko.stream.scaladsl.Source]] from the [[pekko.http.scaladsl.server.RequestContext]].
   *
   * @group basic
   */
  def extractDataBytes: Directive1[Source[ByteString, Any]] = BasicDirectives._extractDataBytes

  /**
   * WARNING: This will read the entire request entity into memory and effectively disable streaming.
   *
   * To help protect against excessive memory use, the request will be aborted if the request is larger
   * than allowed by the `pekko.http.parsing.max-to-strict-bytes` configuration setting.
   *
   * Converts the HttpEntity from the [[pekko.http.scaladsl.server.RequestContext]] into an
   * [[pekko.http.scaladsl.model.HttpEntity.Strict]] and extracts it, or fails the route if unable to drain the
   * entire request body within the timeout.
   *
   * @param timeout The directive is failed if the stream isn't completed after the given timeout.
   * @group basic
   */
  def extractStrictEntity(timeout: FiniteDuration): Directive1[HttpEntity.Strict] =
    toStrictEntity(timeout) & extract(_.request.entity.asInstanceOf[HttpEntity.Strict])

  /**
   * WARNING: This will read the entire request entity into memory and effectively disable streaming.
   *
   * To help protect against excessive memory use, the request will be aborted if the request is larger
   * than allowed by the `pekko.http.parsing.max-to-strict-bytes` configuration setting.
   *
   * Converts the HttpEntity from the [[pekko.http.scaladsl.server.RequestContext]] into an
   * [[pekko.http.scaladsl.model.HttpEntity.Strict]] and extracts it, or fails the route if unable to drain the
   * entire request body within the timeout.
   *
   * @param timeout The directive is failed if the stream isn't completed after the given timeout.
   * @group basic
   */
  def extractStrictEntity(timeout: FiniteDuration, maxBytes: Long): Directive1[HttpEntity.Strict] =
    toStrictEntity(timeout, maxBytes) & extract(_.request.entity.asInstanceOf[HttpEntity.Strict])

  /**
   * WARNING: This will read the entire request entity into memory and effectively disable streaming.
   *
   * To help protect against excessive memory use, the request will be aborted if the request is larger
   * than allowed by the `pekko.http.parsing.max-to-strict-bytes` configuration setting.
   *
   * Extracts the [[pekko.http.scaladsl.server.RequestContext]] itself with the strict HTTP entity,
   * or fails the route if unable to drain the entire request body within the timeout.
   *
   * @param timeout The directive is failed if the stream isn't completed after the given timeout.
   * @group basic
   */
  def toStrictEntity(timeout: FiniteDuration): Directive0 =
    extractParserSettings.flatMap { settings =>
      toStrictEntity(timeout, settings.maxToStrictBytes)
    }

  /**
   * WARNING: This will read the entire request entity into memory and effectively disable streaming.
   *
   * To help protect against excessive memory use, the request will be aborted if the request is larger
   * than allowed by the `pekko.http.parsing.max-to-strict-bytes` configuration setting.
   *
   * Extracts the [[pekko.http.scaladsl.server.RequestContext]] itself with the strict HTTP entity,
   * or fails the route if unable to drain the entire request body within the timeout.
   *
   * @param timeout The directive is failed if the stream isn't completed after the given timeout.
   * @group basic
   */
  def toStrictEntity(timeout: FiniteDuration, maxBytes: Long): Directive0 =
    Directive { inner => ctx =>
      import ctx.{ executionContext, materializer }

      ctx.request.entity.toStrict(timeout, maxBytes).recover {
        case _: TimeoutException =>
          throw IllegalRequestException(
            StatusCodes.RequestTimeout,
            ErrorInfo(s"Request timed out after $timeout while waiting for entity data",
              "Consider increasing the timeout for toStrict"))
        case EntityStreamException(info) =>
          throw IllegalRequestException(StatusCodes.BadRequest, info)
      }.flatMap { strictEntity =>
        val newCtx = ctx.mapRequest(_.withEntity(strictEntity))
        inner(())(newCtx)
      }
    }
}

object BasicDirectives extends BasicDirectives {
  private val _extractUnmatchedPath: Directive1[Uri.Path] = extract(_.unmatchedPath)
  private val _extractMatchedPath: Directive1[Uri.Path] = extract(extractMatched)
  private val _extractRequest: Directive1[HttpRequest] = extract(_.request)
  private val _extractUri: Directive1[Uri] = extract(_.request.uri)
  private val _extractExecutionContext: Directive1[ExecutionContextExecutor] = extract(_.executionContext)
  private val _extractMaterializer: Directive1[Materializer] = extract(_.materializer)
  private val _extractLog: Directive1[LoggingAdapter] = extract(_.log)
  private val _extractSettings: Directive1[RoutingSettings] = extract(_.settings)
  private val _extractParserSettings: Directive1[ParserSettings] = extract(_.parserSettings)
  private val _extractRequestContext: Directive1[RequestContext] = extract(scalaIdentityFunction)
  private val _extractRequestEntity: Directive1[RequestEntity] = extract(_.request.entity)
  private val _extractDataBytes: Directive1[Source[ByteString, Any]] = extract(_.request.entity.dataBytes)

  private def extractMatched(ctx: RequestContext) = {
    val unmatchedPath = ctx.unmatchedPath.toString
    val fullPath = ctx.request.uri.path.toString

    require(
      fullPath.endsWith(unmatchedPath),
      s"Unmatched path '$unmatchedPath' wasn't a suffix of full path '$fullPath'. " +
      "This usually means that ctx.unmatchedPath was manipulated inconsistently " +
      "with ctx.request.uri.path")

    Path(fullPath.substring(0, fullPath.length - unmatchedPath.length))
  }
}
