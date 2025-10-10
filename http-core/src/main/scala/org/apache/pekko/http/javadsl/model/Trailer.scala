/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model

import org.apache.pekko.http.scaladsl.{ model => sm }

import scala.collection.immutable

/** Trailing headers for HTTP/2 responses */
trait Trailer {

  /**
   * Returns a copy of this trailer with the given header added to the list of headers.
   */
  def addHeader(header: HttpHeader): Trailer

  /**
   * Returns a copy of this trailer with the given headers added to the list of headers.
   */
  def addHeaders(headers: Iterable[HttpHeader]): Trailer

  /**
   * Returns a copy of this trailer with new headers.
   */
  def withHeaders(headers: Iterable[HttpHeader]): Trailer
}
object Trailer {
  def create(): Trailer = new sm.Trailer(immutable.Seq.empty)
}
