/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Adapted from github.com/twitter/hpack with this license:
 *
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.shaded.com.twitter.hpack;

final class HuffmanEncoder {

  private final int[] codes;
  private final byte[] lengths;

  /**
   * Creates a new Huffman encoder with the specified Huffman coding.
   *
   * @param codes the Huffman codes indexed by symbol
   * @param lengths the length of each Huffman code
   */
  HuffmanEncoder(int[] codes, byte[] lengths) {
    this.codes = codes;
    this.lengths = lengths;
  }

  /**
   * Compresses the input string literal using the Huffman coding, writing the result directly into
   * <code>dst</code> instead of byte by byte to an <code>OutputStream</code>.
   *
   * @param data the string literal to be Huffman encoded
   * @param dst the array to write the Huffman coded string literal to
   * @param dstOffset the position in <code>dst</code> to start writing at
   * @param encodedLength the value of {@link #getEncodedLength(byte[])} for <code>data</code>,
   *     which the caller has typically already computed to decide whether to use Huffman coding and
   *     to reserve room in <code>dst</code>
   */
  public void encode(byte[] data, byte[] dst, int dstOffset, int encodedLength) {
    if (data == null) {
      throw new NullPointerException("data");
    } else if (dst == null) {
      throw new NullPointerException("dst");
    } else if (dstOffset < 0 || encodedLength < 0 || dstOffset + encodedLength > dst.length) {
      throw new IndexOutOfBoundsException(
          "dstOffset "
              + dstOffset
              + ", encodedLength "
              + encodedLength
              + ", dst.length "
              + dst.length);
    }

    int pos = dstOffset;
    long current = 0;
    int n = 0;

    for (byte value : data) {
      int b = value & 0xFF;
      int code = codes[b];
      int nbits = lengths[b];

      current <<= nbits;
      current |= code;
      n += nbits;

      while (n >= 8) {
        n -= 8;
        dst[pos++] = (byte) (current >> n);
      }
    }

    if (n > 0) {
      current <<= (8 - n);
      current |= (0xFF >>> n); // this should be EOS symbol
      dst[pos++] = (byte) current;
    }

    if (pos - dstOffset != encodedLength) {
      throw new IllegalArgumentException(
          "encodedLength "
              + encodedLength
              + " does not match the Huffman encoded length "
              + (pos - dstOffset));
    }
  }

  /**
   * Returns the number of bytes required to Huffman encode the input string literal.
   *
   * @param data the string literal to be Huffman encoded
   * @return the number of bytes required to Huffman encode <code>data</code>
   */
  public int getEncodedLength(byte[] data) {
    if (data == null) {
      throw new NullPointerException("data");
    }
    long len = 0;
    for (byte b : data) {
      len += lengths[b & 0xFF];
    }
    return (int) ((len + 7) >> 3);
  }
}
