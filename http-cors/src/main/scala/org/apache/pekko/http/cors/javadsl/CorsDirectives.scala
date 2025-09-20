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

package org.apache.pekko.http.cors.javadsl

import java.util.function.Supplier

import org.apache.pekko
import pekko.http.cors.CorsJavaMapping.Implicits._
import pekko.http.cors.javadsl.settings.CorsSettings
import pekko.http.cors.scaladsl
import pekko.http.impl.util.JavaMapping
import pekko.http.javadsl.server.{ RejectionHandler, Route }
import pekko.http.javadsl.server.directives.RouteAdapter

object CorsDirectives {

  import pekko.http.cors.scaladsl.{ CorsDirectives => D }

  def cors(inner: Supplier[Route]): Route =
    RouteAdapter {
      D.cors() {
        inner.get.delegate
      }
    }

  def cors(settings: CorsSettings, inner: Supplier[Route]): Route =
    RouteAdapter {
      D.cors(JavaMapping.toScala(settings)) {
        inner.get.delegate
      }
    }

  def corsRejectionHandler: RejectionHandler =
    new RejectionHandler(scaladsl.CorsDirectives.corsRejectionHandler)
}
