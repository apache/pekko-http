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

package org.apache.pekko.http.javadsl.server
package directives

import java.util.Optional
import java.util.function.{ Function => JFunction, Supplier }

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.coding.{ Coder, DictionaryCoder }
import pekko.http.javadsl.model.headers.HttpEncoding
import pekko.http.scaladsl.server.{ Directives => D }
import pekko.util.ByteString

abstract class CodingDirectives extends CacheConditionDirectives {

  /**
   * Rejects the request with an UnacceptedResponseEncodingRejection
   * if the given response encoding is not accepted by the client.
   */
  def responseEncodingAccepted(encoding: HttpEncoding, inner: Supplier[Route]): Route = RouteAdapter {
    D.responseEncodingAccepted(encoding.asScala) {
      inner.get.delegate
    }
  }

  /**
   * Encodes the response with the encoding that is requested by the client via the `Accept-
   * Encoding` header. The response encoding is determined by the rules specified in
   * http://tools.ietf.org/html/rfc7231#section-5.3.4.
   *
   * If the `Accept-Encoding` header is missing or empty or specifies an encoding other than
   * identity, gzip or deflate then no encoding is used.
   */
  def encodeResponse(inner: Supplier[Route]): Route = RouteAdapter {
    D.encodeResponse {
      inner.get.delegate
    }
  }

  /**
   * Encodes the response with the encoding that is requested by the client via the `Accept-
   * Encoding` header. The response encoding is determined by the rules specified in
   * http://tools.ietf.org/html/rfc7231#section-5.3.4.
   *
   * If the `Accept-Encoding` header is missing then the response is encoded using the `first`
   * encoder.
   *
   * If the `Accept-Encoding` header is empty and `NoCoding` is part of the encoders then no
   * response encoding is used. Otherwise the request is rejected.
   *
   * If [encoders] is empty, no encoding is performed.
   */
  def encodeResponseWith(coders: java.lang.Iterable[Coder], inner: Supplier[Route]): Route = RouteAdapter {
    coders.asScala.toList match {
      case head :: tail =>
        D.encodeResponseWith(head._underlyingScalaCoder, tail.map(_._underlyingScalaCoder): _*) {
          inner.get.delegate
        }
      case _ =>
        inner.get.delegate
    }
  }

  /**
   * Encodes the response with the 'dcz' encoding of RFC 9842 if the client accepts it and `lookup` finds the
   * dictionary with the hash in the request's `Available-Dictionary` header. Otherwise the response is encoded like
   * in [[encodeResponse]].
   *
   * All responses get a `Vary: Accept-Encoding, Available-Dictionary` header, as caches must not reuse them for other
   * dictionaries. Following RFC 9842, section 9.3.3, the dictionary is not used for a cross-origin request whose
   * response cannot be read by the client. This looks at `Access-Control-Allow-Origin`, so a directive that adds this
   * header has to be inside this one.
   *
   * @param lookup finds the coder of the dictionary with the given SHA-256 hash. It should not create the coder on
   *               every call, as that hashes the dictionary
   * @since 2.0.0
   */
  def encodeResponseWithDictionary(
      lookup: JFunction[ByteString, Optional[DictionaryCoder]], inner: Supplier[Route]): Route = RouteAdapter {
    D.encodeResponseWithDictionary(scalaLookup(lookup)) {
      inner.get.delegate
    }
  }

  /**
   * Encodes the response with the 'dcz' encoding of RFC 9842 if possible, see the overload without `coders`. Otherwise
   * the response is encoded like in [[encodeResponseWith]], except that a response that could not be encoded is not
   * rejected if a dictionary was found. If `coders` is empty, the default coders are used.
   *
   * @since 2.0.0
   */
  def encodeResponseWithDictionary(
      lookup: JFunction[ByteString, Optional[DictionaryCoder]],
      coders: java.lang.Iterable[Coder],
      inner: Supplier[Route]): Route = RouteAdapter {
    coders.asScala.toList match {
      case head :: tail =>
        D.encodeResponseWithDictionary(scalaLookup(lookup), head._underlyingScalaCoder,
          tail.map(_._underlyingScalaCoder): _*) {
          inner.get.delegate
        }
      case _ =>
        D.encodeResponseWithDictionary(scalaLookup(lookup)) {
          inner.get.delegate
        }
    }
  }

  private def scalaLookup(lookup: JFunction[ByteString, Optional[DictionaryCoder]]) =
    (hash: ByteString) => lookup.apply(hash).toScala.map(_._underlyingScalaCoder)

  /**
   * Decodes the incoming request using the given Decoder.
   * If the request encoding doesn't match the request is rejected with an `UnsupportedRequestEncodingRejection`.
   */
  def decodeRequestWith(coder: Coder, inner: Supplier[Route]): Route = RouteAdapter {
    D.decodeRequestWith(coder._underlyingScalaCoder) {
      inner.get.delegate
    }
  }

  /**
   * Rejects the request with an UnsupportedRequestEncodingRejection if its encoding doesn't match the given one.
   */
  def requestEncodedWith(encoding: HttpEncoding, inner: Supplier[Route]): Route = RouteAdapter {
    D.requestEncodedWith(encoding.asScala) {
      inner.get.delegate
    }
  }

  /**
   * Decodes the incoming request if it is encoded with one of the given
   * encoders. If the request encoding doesn't match one of the given encoders
   * the request is rejected with an `UnsupportedRequestEncodingRejection`.
   * If no decoders are given the default encoders (`Gzip`, `Deflate`, `NoCoding`) are used.
   */
  def decodeRequestWith(coders: java.lang.Iterable[Coder], inner: Supplier[Route]): Route = RouteAdapter {
    D.decodeRequestWith(coders.asScala.map(_._underlyingScalaCoder).toSeq: _*) {
      inner.get.delegate
    }
  }

  /**
   * Decompresses the incoming request if it is `gzip` or `deflate` compressed.
   * Uncompressed requests are passed through untouched.
   * If the request encoded with another encoding the request is rejected with an `UnsupportedRequestEncodingRejection`.
   */
  def decodeRequest(inner: Supplier[Route]): Route = RouteAdapter {
    D.decodeRequest {
      inner.get.delegate
    }
  }

  /**
   * Inspects the response entity and adds a `Content-Encoding: gzip` response header if
   * the entity's media-type is precompressed with gzip and no `Content-Encoding` header is present yet.
   */
  def withPrecompressedMediaTypeSupport(inner: Supplier[Route]): Route = RouteAdapter {
    D.withPrecompressedMediaTypeSupport {
      inner.get.delegate
    }
  }
}
