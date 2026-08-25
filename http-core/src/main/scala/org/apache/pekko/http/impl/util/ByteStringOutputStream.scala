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

import java.io.ByteArrayOutputStream

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString

/**
 * INTERNAL API
 *
 * An [[java.io.OutputStream]] that buffers into a byte array like [[java.io.ByteArrayOutputStream]] but
 * that can hand the buffered data over as a [[pekko.util.ByteString]] without copying it, unlike
 * `ByteArrayOutputStream.toByteArray` which always creates a copy.
 *
 * Derived from the `ByteStringOutputStream` in Apache Pekko gRPC
 * (https://github.com/apache/pekko-grpc/pull/862).
 */
@InternalApi
private[http] final class ByteStringOutputStream(capacity: Int) extends ByteArrayOutputStream(capacity) {

  /**
   * Wraps the bytes written so far in a `ByteString`. The buffer may be shared with the returned
   * `ByteString`, so this stream must not be written to, reset or reused afterwards.
   */
  def toByteStringUnsafe: ByteString =
    if (count < 1) ByteString.empty
    else if (count > (buf.length >> 1))
      // Most of the buffer is used — wrap it to avoid a copy
      ByteString.fromArrayUnsafe(buf, 0, count)
    else
      // Small amount of data in a large buffer — copy to right-size so the rest can be GC'd
      ByteString.fromArray(buf, 0, count)
}
