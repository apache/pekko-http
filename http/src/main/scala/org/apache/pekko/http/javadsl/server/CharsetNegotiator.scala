/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.javadsl.server

import java.lang.{ Iterable => JIterable }
import java.util.Optional

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.http.impl.util.JavaMapping
import pekko.http.javadsl.model.{ HttpCharset, HttpHeader }
import pekko.http.scaladsl.server.{ CharsetNegotiator => ScalaCharsetNegotiator }

/** Selects a supported charset independently of Pekko HTTP marshalling. */
object CharsetNegotiator {

  /**
   * Picks a charset from the finite application alternatives, in iteration order, using the
   * request's modeled `Accept-Charset` headers. A named charset uses its own quality, even when
   * it is zero; a wildcard only applies to unmentioned charsets. If qualities are equal, a
   * named charset wins over a wildcard, then the first alternative wins. If there are no
   * charset preferences, the first alternative is used.
   *
   * Returns an empty optional if there is no acceptable alternative.
   *
   * @since 2.1.0
   */
  def pickCharset(
      requestHeaders: JIterable[? <: HttpHeader],
      alternatives: JIterable[? <: HttpCharset]): Optional[HttpCharset] = {
    val headers = requestHeaders.asScala.iterator.map(JavaMapping.HttpHeader.toScala).toList
    val candidates = alternatives.asScala.iterator.map(JavaMapping.HttpCharset.toScala).toList
    val best: Option[HttpCharset] = new ScalaCharsetNegotiator(headers).pickCharset(candidates)
    best.toJava
  }
}
