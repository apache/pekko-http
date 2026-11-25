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

package org.apache.pekko.http.javadsl.coding

import java.util.function.{ IntFunction, Predicate }

import org.apache.pekko
import pekko.http.javadsl.model.HttpMessage
import pekko.http.javadsl.model.headers.HttpEncoding
import pekko.http.scaladsl.coding.{ Coder => ScalaCoder, Coders }
import pekko.http.scaladsl.model.headers.{ HttpEncoding => ScalaHttpEncoding }
import pekko.stream.javadsl.Flow
import pekko.util.ByteString

/**
 * A route-local HTTP content coder built from reusable stream flows. Use it with
 * `encodeResponseWithFlowCoder` or `decodeRequestWithFlowCoder`; it is not a built-in [[Coder]]
 * accepted by `encodeResponseWith` or `decodeRequestWith`.
 *
 * @since 2.1.0
 */
final class FlowCoder private (private[javadsl] val delegate: ScalaCoder) {
  def encoding(): HttpEncoding = delegate.encoding
}

object FlowCoder {

  /**
   * Creates an explicitly selected coder for a content-coding token. The decoder factory is called
   * once per materialization, with the maximum output chunk size the decoder must respect.
   * Both flows must have independent codec state per materialization; materialized values are discarded.
   *
   * @since 2.1.0
   */
  def create(
      encoding: String,
      encoder: Flow[ByteString, ByteString, ?],
      decoder: IntFunction[? <: Flow[ByteString, ByteString, ?]]): FlowCoder =
    new FlowCoder(Coders.fromFlows(ScalaHttpEncoding.custom(encoding), encoder.asScala,
      size => decoder.apply(size).asScala))

  /**
   * Creates a coder with a custom filter accepting response messages or any of their supertypes.
   *
   * @since 2.1.0
   */
  def create(
      encoding: String,
      encoder: Flow[ByteString, ByteString, ?],
      decoder: IntFunction[? <: Flow[ByteString, ByteString, ?]],
      messageFilter: Predicate[? >: HttpMessage]): FlowCoder =
    new FlowCoder(Coders.fromFlows(
      ScalaHttpEncoding.custom(encoding),
      encoder.asScala,
      size => decoder.apply(size).asScala,
      message => messageFilter.test(message)))
}
