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

package org.apache.pekko.http.impl.util

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * This object contains HTTP related constants that are used in various places.
 * It is not intended to be used outside of the HTTP implementation.
 */
@InternalApi
private[http] object HttpConstants {
  final val CR_BYTE: Byte = 13
  final val LF_BYTE: Byte = 10
  final val SPACE_BYTE: Byte = 32
  final val DASH_BYTE: Byte = 45 // '-' (minus, dash, hyphen)
}
