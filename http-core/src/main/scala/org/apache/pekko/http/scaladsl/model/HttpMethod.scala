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

package org.apache.pekko.http.scaladsl.model

import java.util.Locale

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.javadsl.{ model => jm }
import pekko.http.scaladsl.model.RequestEntityAcceptance._

sealed trait RequestEntityAcceptance extends jm.RequestEntityAcceptance {
  def isEntityAccepted: Boolean
}
object RequestEntityAcceptance {
  case object Expected extends RequestEntityAcceptance {
    override def isEntityAccepted: Boolean = true
  }
  case object Tolerated extends RequestEntityAcceptance {
    override def isEntityAccepted: Boolean = true
  }
  case object Disallowed extends RequestEntityAcceptance {
    override def isEntityAccepted: Boolean = false
  }
}

/**
 * The method of an HTTP request.
 * @param isSafe true if the resource should not be altered on the server
 * @param isIdempotent true if requests can be safely (& automatically) repeated
 * @param requestEntityAcceptance Expected if meaning of request entities is properly defined
 */
final case class HttpMethod private[http] (
    override val value: String,
    isSafe: Boolean,
    isIdempotent: Boolean,
    requestEntityAcceptance: RequestEntityAcceptance) extends jm.HttpMethod with SingletonValueRenderable {
  override def isEntityAccepted: Boolean = requestEntityAcceptance.isEntityAccepted
  override def toString: String = s"HttpMethod($value)"
}

object HttpMethod {
  def custom(name: String, safe: Boolean, idempotent: Boolean, requestEntityAcceptance: RequestEntityAcceptance)
      : HttpMethod = {
    require(name.nonEmpty, "value must be non-empty")
    require(!safe || idempotent, "An HTTP method cannot be safe without being idempotent")
    apply(name, safe, idempotent, requestEntityAcceptance)
  }

  /**
   * Creates a custom method by name and assumes properties conservatively to be
   * safe = false, idempotent = false and requestEntityAcceptance = Expected.
   */
  def custom(name: String): HttpMethod =
    custom(name, safe = false, idempotent = false, requestEntityAcceptance = Expected)
}

object HttpMethods extends ObjectRegistry[String, HttpMethod] {
  private def register(method: HttpMethod): HttpMethod = register(method.value, method)

  // format: OFF
  val CONNECT = register(HttpMethod("CONNECT", isSafe = false, isIdempotent = false, requestEntityAcceptance = Disallowed))
  val DELETE  = register(HttpMethod("DELETE" , isSafe = false, isIdempotent = true , requestEntityAcceptance = Tolerated))
  val GET     = register(HttpMethod("GET"    , isSafe = true , isIdempotent = true , requestEntityAcceptance = Tolerated))
  val HEAD    = register(HttpMethod("HEAD"   , isSafe = true , isIdempotent = true , requestEntityAcceptance = Disallowed))
  val OPTIONS = register(HttpMethod("OPTIONS", isSafe = true , isIdempotent = true , requestEntityAcceptance = Expected))
  val PATCH   = register(HttpMethod("PATCH"  , isSafe = false, isIdempotent = false, requestEntityAcceptance = Expected))
  val POST    = register(HttpMethod("POST"   , isSafe = false, isIdempotent = false, requestEntityAcceptance = Expected))
  val PUT     = register(HttpMethod("PUT"    , isSafe = false, isIdempotent = true , requestEntityAcceptance = Expected))
  val TRACE   = register(HttpMethod("TRACE"  , isSafe = true , isIdempotent = true , requestEntityAcceptance = Disallowed))
  // format: ON

  override def getForKeyCaseInsensitive(key: String)(implicit conv: String <:< String): Option[HttpMethod] =
    getForKey(conv(key.toUpperCase(Locale.ROOT)))
}
