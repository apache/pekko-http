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

package org.apache.pekko.http.javadsl.model

import java.util.concurrent.CompletableFuture

import org.apache.pekko
import pekko.annotation.ApiMayChange

/**
 * A marker trait for attribute values that should be (automatically) carried over from request to response.
 */
@ApiMayChange
trait RequestResponseAssociation

/**
 * An association for completing a future when the response arrives.
 */
final class ResponseFuture(val future: CompletableFuture[HttpResponse])
    extends pekko.http.scaladsl.model.RequestResponseAssociation
object ResponseFuture {
  val KEY = AttributeKey.create[ResponseFuture]("association-future-handle", classOf[ResponseFuture])
  def apply(promise: CompletableFuture[HttpResponse]): ResponseFuture = new ResponseFuture(promise)
}
