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

import java.io.OutputStream
import java.util.Arrays

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString

/**
 * INTERNAL API
 *
 * An [[java.io.OutputStream]] that buffers into a byte array like [[java.io.ByteArrayOutputStream]] but
 * that hands the buffered data over as a [[pekko.util.ByteString]] without copying it, unlike
 * `ByteArrayOutputStream.toByteArray` which always creates a copy, and whose writes are not
 * `synchronized`: every user is a single stage that owns the stream, and the per-call monitor was a
 * measurable share of the cost of encoding an HPACK header block.
 *
 * After a hand-over the stream is empty again and can be reused for the next block of data.
 *
 * Not thread-safe.
 *
 * Derived from the `ByteStringOutputStream` in Apache Pekko gRPC
 * (https://github.com/apache/pekko-grpc/pull/862).
 */
@InternalApi
private[http] final class ByteStringOutputStream(initialCapacity: Int) extends OutputStream {
  if (initialCapacity < 0) throw new IllegalArgumentException(s"Illegal initial capacity: $initialCapacity")

  private[this] var buf: Array[Byte] = Array.emptyByteArray
  private[this] var count: Int = 0
  // the size of the last block handed over; the array for the next block is allocated to hold it
  private[this] var lastCount: Int = 0

  /** The number of bytes written since the last hand-over. */
  def size: Int = count

  override def write(b: Int): Unit = {
    ensureCapacity(1)
    buf(count) = b.toByte
    count += 1
  }

  override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
    ensureCapacity(length)
    System.arraycopy(bytes, offset, buf, count, length)
    count += length
  }

  /**
   * Reserves `length` bytes at the end of the buffered data for the caller to fill in directly.
   *
   * @return the position in [[array]] at which the reserved bytes start
   */
  def reserve(length: Int): Int = {
    ensureCapacity(length)
    val position = count
    count += length
    position
  }

  /**
   * The array backing the buffered data. Only valid until the next write, reserve or hand-over, any of
   * which may replace it.
   */
  def array: Array[Byte] = buf

  /**
   * Hands the buffered data over as a `ByteString` and leaves the stream empty for the next block.
   *
   * When most of the buffer is used the `ByteString` wraps the array without copying and the stream
   * starts a fresh array for the next block; when only a small part is used the data is copied so the
   * `ByteString` does not retain a large, mostly unused array, and the stream keeps its array.
   */
  def takeByteString(): ByteString =
    if (count < 1) ByteString.empty
    else {
      val result =
        if (count > (buf.length >> 1)) {
          val wrapped = ByteString.fromArrayUnsafe(buf, 0, count)
          lastCount = count
          buf = Array.emptyByteArray
          wrapped
        } else ByteString.fromArray(buf, 0, count)
      count = 0
      result
    }

  private def ensureCapacity(additional: Int): Unit = {
    val required = count + additional
    if (required > buf.length) {
      val capacity =
        if (buf.length == 0) math.max(math.max(initialCapacity, lastCount), required)
        else math.max(buf.length << 1, required)
      buf = Arrays.copyOf(buf, capacity)
    }
  }
}
