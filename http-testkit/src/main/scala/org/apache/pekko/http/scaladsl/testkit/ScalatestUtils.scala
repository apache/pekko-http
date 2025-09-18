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

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.Try

import org.apache.pekko
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import pekko.stream.Materializer

import org.scalatest.Suite
import org.scalatest.matchers
import org.scalatest.matchers.Matcher

trait ScalatestUtils extends MarshallingTestUtils {
  import matchers.should.Matchers._

  def evaluateTo[T](value: T): Matcher[Future[T]] =
    equal(value).matcher[T].compose(x => Await.result(x, marshallingTimeout))

  def haveFailedWith(t: Throwable): Matcher[Future[_]] =
    equal(t).matcher[Throwable].compose(x => Await.result(x.failed, marshallingTimeout))

  def unmarshalToValue[T: FromEntityUnmarshaller](value: T)(
      implicit ec: ExecutionContext, mat: Materializer): Matcher[HttpEntity] =
    equal(value).matcher[T].compose(unmarshalValue(_))

  def unmarshalTo[T: FromEntityUnmarshaller](value: Try[T])(
      implicit ec: ExecutionContext, mat: Materializer): Matcher[HttpEntity] =
    equal(value).matcher[Try[T]].compose(unmarshal(_))
}

trait ScalatestRouteTest extends RouteTest with TestFrameworkInterface.Scalatest with ScalatestUtils { this: Suite => }
