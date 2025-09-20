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

package org.apache.pekko.http.scaladsl.testkit

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.util.Try

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.marshalling._
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, MediaRange }
import pekko.http.scaladsl.model.headers.Accept
import pekko.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshal }
import pekko.stream.Materializer

import com.typesafe.config.Config

trait MarshallingTestUtils {

  def testConfig: Config

  def marshallingTimeout = testConfig.getFiniteDuration("pekko.http.testkit.marshalling.timeout")

  def marshal[T: ToEntityMarshaller](value: T)(implicit ec: ExecutionContext, mat: Materializer): HttpEntity.Strict =
    Await.result(Marshal(value).to[HttpEntity].flatMap(_.toStrict(marshallingTimeout)), 2 * marshallingTimeout)

  def marshalToResponseForRequestAccepting[T: ToResponseMarshaller](value: T, mediaRanges: MediaRange*)(
      implicit ec: ExecutionContext): HttpResponse =
    marshalToResponse(value, HttpRequest(headers = Accept(mediaRanges.toList) :: Nil))

  def marshalToResponse[T: ToResponseMarshaller](value: T, request: HttpRequest = HttpRequest())(
      implicit ec: ExecutionContext): HttpResponse =
    Await.result(Marshal(value).toResponseFor(request), marshallingTimeout)

  def unmarshalValue[T: FromEntityUnmarshaller](entity: HttpEntity)(
      implicit ec: ExecutionContext, mat: Materializer): T =
    unmarshal(entity).get

  def unmarshal[T: FromEntityUnmarshaller](entity: HttpEntity)(
      implicit ec: ExecutionContext, mat: Materializer): Try[T] = {
    val fut = Unmarshal(entity).to[T]
    Await.ready(fut, marshallingTimeout)
    fut.value.get
  }
}
