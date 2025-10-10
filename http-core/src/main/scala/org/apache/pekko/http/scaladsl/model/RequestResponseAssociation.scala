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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.annotation.InternalStableApi

import scala.concurrent.Promise

/**
 * A marker trait for attribute values that should be (automatically) carried over from request to response.
 */
@ApiMayChange
@InternalStableApi
trait RequestResponseAssociation extends pekko.http.javadsl.model.RequestResponseAssociation

/**
 * A simple value holder class implementing RequestResponseAssociation.
 */
@ApiMayChange
final case class SimpleRequestResponseAttribute[T](value: T) extends RequestResponseAssociation

/**
 * An association for completing a future when the response arrives.
 */
final class ResponsePromise(val promise: Promise[HttpResponse]) extends RequestResponseAssociation
object ResponsePromise {
  val Key = AttributeKey[ResponsePromise]("association-future-handle")
  def apply(promise: Promise[HttpResponse]): ResponsePromise = new ResponsePromise(promise)
}
