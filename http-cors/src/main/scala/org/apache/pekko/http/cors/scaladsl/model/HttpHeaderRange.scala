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

package org.apache.pekko.http.cors.scaladsl.model

import scala.collection.immutable.Seq

import org.apache.pekko
import pekko.http.cors.CorsJavaMapping.Implicits._
import pekko.http.cors.javadsl
import pekko.http.impl.util.JavaMapping
import pekko.util.Helpers

sealed abstract class HttpHeaderRange extends javadsl.model.HttpHeaderRange {
  override def concat(range: javadsl.model.HttpHeaderRange): HttpHeaderRange

  /**
   * Operator alias for [[concat]].
   *
   * @since 2.0.0
   */
  def ++(range: javadsl.model.HttpHeaderRange): HttpHeaderRange = concat(range)
}

object HttpHeaderRange {
  case object `*` extends HttpHeaderRange {
    def matches(header: String) = true

    override def concat(range: javadsl.model.HttpHeaderRange): HttpHeaderRange = this
  }

  final case class Default(headers: Seq[String]) extends HttpHeaderRange {
    private val lowercaseHeaders: Seq[String] = headers.map(Helpers.toRootLowerCase)
    def matches(header: String): Boolean = lowercaseHeaders.contains(Helpers.toRootLowerCase(header))

    override def concat(range: javadsl.model.HttpHeaderRange): HttpHeaderRange = {
      JavaMapping.toScala(range) match {
        case `*`              => `*`
        case Default(headers) => Default(this.headers ++ headers)
      }
    }
  }

  def apply(headers: String*): Default = Default(Seq(headers: _*))
}
