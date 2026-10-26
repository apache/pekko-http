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

package org.apache.pekko.http.scaladsl.model.ws

/**
 * Signals that no WebSocket frame was received or sent within the configured `receive-idle-timeout` or
 * `send-idle-timeout` (see [[pekko.http.scaladsl.settings.WebSocketSettings]]).
 *
 * This is deliberately not a `java.util.concurrent.TimeoutException` (such as a
 * [[pekko.stream.StreamIdleTimeoutException]]): the client side TCP connection reports any `TimeoutException`
 * passing through it as a TCP idle timeout.
 *
 * @since 2.0.0
 */
final class WebSocketIdleTimeoutException(message: String) extends RuntimeException(message)
