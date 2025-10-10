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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.http.javadsl.{ model => jm }

import scala.collection.immutable

class Trailer(@ApiMayChange val headers: immutable.Seq[(String, String)]) extends jm.Trailer {

  /**
   * Java API
   */
  override def addHeader(header: jm.HttpHeader): Trailer = addHeaders(Seq(header))

  /**
   * Java API
   */
  override def addHeaders(headers: Iterable[jm.HttpHeader]): Trailer =
    new Trailer(this.headers ++ headers.map { header =>
      val sheader = header.asInstanceOf[HttpHeader]
      (sheader.name, sheader.value)
    })

  /**
   * Java API
   */
  override def withHeaders(headers: Iterable[jm.HttpHeader]): Trailer = Trailer(immutable.Seq.empty).addHeaders(headers)
}
object Trailer {
  def apply(): Trailer = new Trailer(immutable.Seq.empty)
  def apply(header: HttpHeader): Trailer =
    new Trailer(immutable.Seq(header).map(h => (h.name, h.value)))
  def apply(headers: immutable.Seq[HttpHeader]) = new Trailer(immutable.Seq.empty).addHeaders(headers)
}
