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

package org.apache.pekko.http.scaladsl.server
package directives

import scala.util.control.NonFatal

import org.apache.pekko
import pekko.http.scaladsl.coding._
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ HttpEncoding, HttpEncodings }
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

/**
 * @groupname coding Coding directives
 * @groupprio coding 50
 */
trait CodingDirectives {
  import BasicDirectives._
  import CodingDirectives._
  import MiscDirectives._
  import RouteDirectives._

  // encoding

  /**
   * Rejects the request with an UnacceptedResponseEncodingRejection
   * if the given response encoding is not accepted by the client.
   *
   * @group coding
   */
  def responseEncodingAccepted(encoding: HttpEncoding): Directive0 =
    extractRequest.flatMap { request =>
      if (EncodingNegotiator(request.headers).isAccepted(encoding)) pass
      else reject(UnacceptedResponseEncodingRejection(Set(encoding)))
    }

  /**
   * Encodes the response with the encoding that is requested by the client via the `Accept-
   * Encoding` header. The response encoding is determined by the rules specified in
   * http://tools.ietf.org/html/rfc7231#section-5.3.4.
   *
   * If the `Accept-Encoding` header is missing or empty or specifies an encoding other than
   * identity, gzip or deflate then no encoding is used.
   *
   * @group coding
   */
  def encodeResponse: Directive0 =
    _encodeResponse(DefaultEncodeResponseEncoders)

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
   * @group coding
   */
  def encodeResponseWith(first: Encoder, more: Encoder*): Directive0 =
    _encodeResponse(Seq(first +: more: _*))

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
   * @param lookup finds the coder of the dictionary with the given SHA-256 hash, see [[Coders.Dcz]]. It should not
   *               create the coder on every call, as that hashes the dictionary
   * @group coding
   * @since 2.0.0
   */
  def encodeResponseWithDictionary(lookup: ByteString => Option[DictionaryCoder]): Directive0 =
    _encodeResponseWithDictionary(lookup, DefaultEncodeResponseEncoders)

  /**
   * Encodes the response with the 'dcz' encoding of RFC 9842 if possible, see
   * [[encodeResponseWithDictionary(lookup:* encodeResponseWithDictionary]]. Otherwise the response is encoded like in
   * [[encodeResponseWith]], except that a response that could not be encoded is not rejected if a dictionary was found.
   *
   * @group coding
   * @since 2.0.0
   */
  def encodeResponseWithDictionary(
      lookup: ByteString => Option[DictionaryCoder], first: Encoder, more: Encoder*): Directive0 =
    _encodeResponseWithDictionary(lookup, Seq(first +: more: _*))

  // decoding

  /**
   * Decodes the incoming request using the given Decoder.
   * If the request encoding doesn't match the request is rejected with an `UnsupportedRequestEncodingRejection`.
   *
   * @group coding
   */
  def decodeRequestWith(decoder: Decoder): Directive0 = {
    def applyDecoder: Directive0 =
      if (decoder == Coders.NoCoding) pass
      else
        extractSettings.flatMap { settings =>
          val effectiveDecoder = decoder.withMaxBytesPerChunk(settings.decodeMaxBytesPerChunk)
          mapRequest { msg =>
            effectiveDecoder.decodeMessage(msg)
              .transformEntityDataBytes(Flow[ByteString].mapError {
                case NonFatal(e) =>
                  IllegalRequestException(
                    StatusCodes.BadRequest,
                    ErrorInfo("The request's encoding is corrupt", e.getMessage))
              })
          } & withSizeLimit(settings.decodeMaxSize)
        }

    requestEntityEmpty | (
      requestEncodedWith(decoder.encoding) &
      applyDecoder &
      cancelRejections(classOf[UnsupportedRequestEncodingRejection]))
  }

  /**
   * Rejects the request with an UnsupportedRequestEncodingRejection if its encoding doesn't match the given one.
   *
   * @group coding
   */
  def requestEncodedWith(encoding: HttpEncoding): Directive0 =
    extract(_.request.encoding).flatMap {
      case `encoding` => pass
      case _          => reject(UnsupportedRequestEncodingRejection(encoding))
    }

  /**
   * Decodes the incoming request if it is encoded with one of the given
   * encoders. If the request encoding doesn't match one of the given encoders
   * the request is rejected with an `UnsupportedRequestEncodingRejection`.
   * If no decoders are given the default encoders (`Gzip`, `Deflate`, `NoCoding`) are used.
   *
   * @group coding
   */
  def decodeRequestWith(decoders: Decoder*): Directive0 =
    theseOrDefault(decoders).map(decodeRequestWith).reduce(_ | _)

  /**
   * Decompresses the incoming request if it is `gzip` or `deflate` compressed.
   * Uncompressed requests are passed through untouched.
   * If the request encoded with another encoding the request is rejected with an `UnsupportedRequestEncodingRejection`.
   *
   * @group coding
   */
  def decodeRequest: Directive0 =
    decodeRequestWith(DefaultCoders: _*)

  /**
   * Inspects the response entity and adds a `Content-Encoding: gzip` response header if
   * the entity's media-type is precompressed with gzip and no `Content-Encoding` header is present yet.
   *
   * @group coding
   */
  def withPrecompressedMediaTypeSupport: Directive0 =
    mapResponse { response =>
      if (response.entity.contentType.mediaType.comp != MediaType.Gzipped) response
      else response.withDefaultHeaders(headers.`Content-Encoding`(HttpEncodings.gzip))
    }
}

object CodingDirectives extends CodingDirectives {
  def DefaultCoders: Seq[Coder] = Coders.DefaultCoders

  // same entries as DefaultCoders but in different order
  private[http] val DefaultEncodeResponseEncoders = Seq(Coders.NoCoding, Coders.Gzip, Coders.Deflate)

  def theseOrDefault[T >: Coder](these: Seq[T]): Seq[T] = if (these.isEmpty) DefaultCoders else these

  import BasicDirectives._
  import RouteDirectives._

  private def _encodeResponse(encoders: Seq[Encoder]): Directive0 =
    BasicDirectives.extractRequest.flatMap { request =>
      val negotiator = EncodingNegotiator(request.headers)
      val encodings: List[HttpEncoding] = encoders.map(_.encoding).toList
      val bestEncoder = negotiator.pickEncoding(encodings).flatMap(be => encoders.find(_.encoding == be))
      bestEncoder match {
        case Some(encoder) => mapResponse(encoder.encodeMessage(_))
        case _             =>
          if (encoders.contains(Coders.NoCoding) && !negotiator.hasMatchingFor(HttpEncodings.identity)) pass
          else reject(UnacceptedResponseEncodingRejection(encodings.toSet))
      }
    }

  private val DictionaryVaryHeader = headers.RawHeader("Vary", "Accept-Encoding, Available-Dictionary")

  private def _encodeResponseWithDictionary(
      lookup: ByteString => Option[DictionaryCoder], encoders: Seq[Encoder]): Directive0 =
    BasicDirectives.extractRequest.flatMap { request =>
      def addVary(response: HttpResponse): HttpResponse = response.addHeader(DictionaryVaryHeader)

      val dictionaryCoder =
        if (acceptsDcz(request))
          request.header[headers.`Available-Dictionary`].flatMap { available =>
            lookup(available.hash).filter(_.dictionaryHash == available.hash)
          }
        else None

      dictionaryCoder match {
        case Some(coder) =>
          val fallback = EncodingNegotiator(request.headers)
            .pickEncoding(encoders.map(_.encoding).toList)
            .flatMap(encoding => encoders.find(_.encoding == encoding))
          mapResponse { response =>
            val encoded: HttpResponse =
              if (dictionaryAllowedFor(request, response)) coder.encodeMessage(response) else response
            // does nothing if the dictionary was used, as there is a Content-Encoding header then
            addVary(fallback.fold(encoded)(_.encodeMessage(encoded)))
          }
        case None => mapResponse(addVary) & _encodeResponse(encoders)
      }
    }

  // Only an explicit 'dcz' counts: a client sends it only if it has the dictionary, which a wildcard says nothing about
  private def acceptsDcz(request: HttpRequest): Boolean =
    request.headers.exists {
      case headers.`Accept-Encoding`(encodings) =>
        encodings.exists {
          case headers.HttpEncodingRange.One(encoding, qValue) => encoding == HttpEncodings.dcz && qValue > 0f
          case _                                               => false
        }
      case _ => false
    }

  // https://www.rfc-editor.org/rfc/rfc9842#section-9.3.3
  private def dictionaryAllowedFor(request: HttpRequest, response: HttpResponse): Boolean = {
    def headerValue(lowercaseName: String) = request.headers.find(_.is(lowercaseName)).map(_.value)

    headerValue("sec-fetch-site") match {
      case None | Some("same-origin") => true
      case _                          =>
        headerValue("sec-fetch-mode") match {
          case None | Some("navigate") | Some("same-origin") => true
          case Some("cors")                                  =>
            (response.header[headers.`Access-Control-Allow-Origin`], request.header[headers.Origin]) match {
              case (Some(allowed), Some(origin)) => allowed.value == "*" || allowed.value == origin.value
              case _                             => false
            }
          case _ => false
        }
    }
  }
}
