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

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.AnyMatchers._
import org.specs2.matcher.ExceptionMatchers._
import org.specs2.matcher.FutureMatchers._
import org.specs2.matcher.Matcher

import org.apache.pekko
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.unmarshalling._
import pekko.stream.Materializer

trait Specs2Utils extends MarshallingTestUtils {

  def evaluateTo[T](value: T)(implicit ee: ExecutionEnv): Matcher[Future[T]] =
    beEqualTo(value).await

  def haveFailedWith(t: Throwable)(implicit ee: ExecutionEnv): Matcher[Future[_]] =
    throwA(t).await

  def unmarshalToValue[T: FromEntityUnmarshaller](value: T)(
      implicit ec: ExecutionContext, mat: Materializer): Matcher[HttpEntity] =
    beEqualTo(value).^^(unmarshalValue(_: HttpEntity))

  def unmarshalTo[T: FromEntityUnmarshaller](value: Try[T])(
      implicit ec: ExecutionContext, mat: Materializer): Matcher[HttpEntity] =
    beEqualTo(value).^^(unmarshal(_: HttpEntity))
}

trait Specs2RouteTest extends RouteTest with Specs2FrameworkInterface.Specs2 with Specs2Utils
