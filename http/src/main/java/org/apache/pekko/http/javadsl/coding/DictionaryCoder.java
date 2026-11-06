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

package org.apache.pekko.http.javadsl.coding;

import java.util.Collections;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.coding.Coders$;
import org.apache.pekko.http.scaladsl.coding.Decoder;
import org.apache.pekko.http.scaladsl.coding.Encoder$;
import org.apache.pekko.http.scaladsl.coding.Zstd$;
import org.apache.pekko.http.scaladsl.model.HttpMessage;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.util.ByteString;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.FutureConverters;

/**
 * A coder for the 'dcz' encoding of RFC 9842, which compresses with a dictionary that both sides
 * have. It needs the optional {@code com.github.luben:zstd-jni} dependency on the classpath,
 * version 1.5.7-25 or later.
 *
 * <p>Creating one hashes the dictionary, so create one per dictionary and reuse it.
 *
 * @since 2.0.0
 */
public final class DictionaryCoder {
  private final org.apache.pekko.http.scaladsl.coding.DictionaryCoder underlying;

  private DictionaryCoder(org.apache.pekko.http.scaladsl.coding.DictionaryCoder underlying) {
    this.underlying = underlying;
  }

  /**
   * Creates a coder with the default compression level.
   *
   * @param dictionary the dictionary, which is used as raw content. It must not be empty, and must
   *     not start with the zstd dictionary magic number, as such a dictionary cannot be loaded as
   *     raw content
   */
  public static DictionaryCoder create(ByteString dictionary) {
    return create(dictionary, Zstd$.MODULE$.DefaultCompressionLevel());
  }

  /**
   * Creates a coder with the given compression level.
   *
   * @param dictionary the dictionary, see {@link #create(ByteString)}
   * @param compressionLevel must be between {@code Compression.ZstdMinCompressionLevel} and {@code
   *     Compression.ZstdMaxCompressionLevel} of pekko-stream
   */
  public static DictionaryCoder create(ByteString dictionary, int compressionLevel) {
    return new DictionaryCoder(
        Coders$.MODULE$.Dcz(dictionary, Encoder$.MODULE$.DefaultFilter(), compressionLevel));
  }

  /** The SHA-256 hash of the dictionary, which clients send in the `Available-Dictionary` header */
  public ByteString getDictionaryHash() {
    return underlying.dictionaryHash();
  }

  public HttpResponse encodeMessage(HttpResponse message) {
    return (HttpResponse) underlying.encodeMessage((HttpMessage) message);
  }

  public HttpRequest encodeMessage(HttpRequest message) {
    return (HttpRequest) underlying.encodeMessage((HttpMessage) message);
  }

  /**
   * Decodes the response if its {@code Content-Encoding} header is {@code dcz}, and otherwise
   * returns it unchanged.
   */
  public HttpResponse decodeResponse(HttpResponse response) {
    return (HttpResponse) Coders$.MODULE$.decodeMessage((HttpMessage) response, decoders());
  }

  /**
   * Decodes the request if its {@code Content-Encoding} header is {@code dcz}, and otherwise
   * returns it unchanged.
   */
  public HttpRequest decodeRequest(HttpRequest request) {
    return (HttpRequest) Coders$.MODULE$.decodeMessage((HttpMessage) request, decoders());
  }

  public CompletionStage<ByteString> decode(ByteString input, Materializer mat) {
    return FutureConverters.asJava(underlying.decode(input, mat));
  }

  public org.apache.pekko.http.scaladsl.coding.DictionaryCoder _underlyingScalaCoder() {
    return underlying;
  }

  private scala.collection.immutable.Seq<Decoder> decoders() {
    return CollectionConverters.asScala(Collections.<Decoder>singletonList(underlying)).toList();
  }
}
