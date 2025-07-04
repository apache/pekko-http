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

package org.apache.pekko.http.scaladsl.model.headers

import java.lang.Iterable
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util
import javax.net.ssl.SSLSession
import org.apache.pekko
import pekko.annotation.{ ApiMayChange, InternalApi }
import pekko.stream.scaladsl.ScalaSessionAPI

import scala.collection.immutable.TreeMap
import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }
import scala.annotation.tailrec
import scala.collection.immutable
import org.parboiled2.util.Base64
import pekko.event.Logging
import pekko.http.ccompat.{ pre213, since213 }
import pekko.http.impl.util._
import pekko.http.impl.model.parser.CharacterClasses.`attr-char`
import pekko.http.javadsl.{ model => jm }
import pekko.http.scaladsl.model._

sealed abstract class ModeledCompanion[T: ClassTag] extends Renderable {
  val name = ModeledCompanion.nameFromClass(getClass)
  val lowercaseName = name.toRootLowerCase
  private[this] val nameAndColonSpaceBytes = (name + ": ").asciiBytes
  final def render[R <: Rendering](r: R): r.type = r ~~ nameAndColonSpaceBytes

  /**
   * Parses the given value into a header of this type. Returns `Right[T]` if parsing
   * was successful and `Left(errors)` otherwise.
   */
  def parseFromValueString(value: String): Either[List[ErrorInfo], T] =
    HttpHeader.parse(name, value) match {
      case HttpHeader.ParsingResult.Ok(header: T, Nil) => Right(header)
      case res                                         => Left(res.errors)
    }
}

/** INTERNAL API */
@InternalApi
private[pekko] object ModeledCompanion {
  def nameFromClass[T](clazz: Class[T]): String = {
    val name = {
      val n = Logging.simpleName(clazz).replace("$minus", "-")
      if (n.last == '$') n.dropRight(1) // drop trailing $
      else n
    }

    val dollarIndex = name.indexOf('$')
    if (dollarIndex != -1) name.drop(dollarIndex + 1)
    else name
  }
}

sealed trait ModeledHeader extends HttpHeader with Serializable {
  def renderInRequests: Boolean = false // default implementation
  def renderInResponses: Boolean = false // default implementation
  def name: String = companion.name
  private[this] var _value: String = _
  def value: String = {
    if (_value eq null) _value = renderValue(new StringRendering).get
    _value
  }
  def lowercaseName: String = companion.lowercaseName
  final def render[R <: Rendering](r: R): r.type = renderValue(companion.render(r))
  protected[http] def renderValue[R <: Rendering](r: R): r.type
  protected def companion: ModeledCompanion[_]
}

private[headers] sealed trait RequestHeader extends ModeledHeader { override def renderInRequests = true }
private[headers] sealed trait ResponseHeader extends ModeledHeader { override def renderInResponses = true }
private[headers] sealed trait RequestResponseHeader extends RequestHeader with ResponseHeader
private[headers] sealed trait SyntheticHeader extends ModeledHeader

/**
 * Superclass for user-defined custom headers defined by implementing `name` and `value`.
 *
 * Prefer to extend [[ModeledCustomHeader]] and [[ModeledCustomHeaderCompanion]] instead if
 * planning to use the defined header in match clauses (e.g. in the routing layer of Pekko HTTP),
 * as they allow the custom header to be matched from [[RawHeader]] and vice-versa.
 */
abstract class CustomHeader extends jm.headers.CustomHeader {
  def lowercaseName: String = name.toRootLowerCase
  final def render[R <: Rendering](r: R): r.type = r ~~ name ~~ ':' ~~ ' ' ~~ value
}

/**
 * To be extended by companion object of a custom header extending [[ModeledCustomHeader]].
 * Implements necessary apply and unapply methods to make the such defined header feel "native".
 */
abstract class ModeledCustomHeaderCompanion[H <: ModeledCustomHeader[H]] {
  def name: String
  private var _lowercaseName: String = _
  def lowercaseName: String = {
    if (_lowercaseName eq null) _lowercaseName = name.toRootLowerCase
    _lowercaseName
  }

  def parse(value: String): Try[H]

  def apply(value: String): H =
    parse(value) match {
      case Success(parsed) => parsed
      case Failure(ex) =>
        throw new IllegalArgumentException(s"Unable to construct custom header by parsing: '$value'", ex)
    }

  def unapply(h: HttpHeader): Option[String] = h match {
    case _: RawHeader    => if (h.lowercaseName == lowercaseName) Some(h.value) else None
    case _: CustomHeader => if (h.lowercaseName == lowercaseName) Some(h.value) else None
    case _               => None
  }

  final implicit val implicitlyLocatableCompanion: ModeledCustomHeaderCompanion[H] = this
}

/**
 * Support class for building user-defined custom headers defined by implementing `name` and `value`.
 * By implementing a [[ModeledCustomHeader]] instead of [[CustomHeader]] directly, all needed unapply
 * methods are provided for this class, such that it can be pattern matched on from [[RawHeader]] and
 * the other way around as well.
 */
abstract class ModeledCustomHeader[H <: ModeledCustomHeader[H]] extends CustomHeader { this: H =>
  def companion: ModeledCustomHeaderCompanion[H]

  final override def name = companion.name
  final override def lowercaseName = companion.lowercaseName
}

import pekko.http.impl.util.JavaMapping.Implicits._

// https://tools.ietf.org/html/rfc7231#section-5.3.2
object Accept extends ModeledCompanion[Accept] {
  @pre213
  def apply(mediaRanges: MediaRange*): Accept =
    apply(immutable.Seq(mediaRanges: _*))
  @since213
  def apply(firstMediaRange: MediaRange, otherMediaRanges: MediaRange*): Accept =
    apply(firstMediaRange +: otherMediaRanges)
  implicit val mediaRangesRenderer: Renderer[immutable.Iterable[MediaRange]] = Renderer.defaultSeqRenderer[MediaRange] // cache
}
final case class Accept(mediaRanges: immutable.Seq[MediaRange]) extends jm.headers.Accept with RequestHeader {
  import Accept.mediaRangesRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ mediaRanges
  protected def companion = Accept
  def acceptsAll = mediaRanges.exists(mr => mr.isWildcard && mr.qValue > 0f)

  /** Java API */
  def getMediaRanges: Iterable[jm.MediaRange] = mediaRanges.asJava
}

// https://tools.ietf.org/html/rfc7231#section-5.3.3
object `Accept-Charset` extends ModeledCompanion[`Accept-Charset`] {
  def apply(first: HttpCharsetRange, more: HttpCharsetRange*): `Accept-Charset` =
    apply(immutable.Seq(first +: more: _*))
  implicit val charsetRangesRenderer: Renderer[immutable.Iterable[HttpCharsetRange]] =
    Renderer.defaultSeqRenderer[HttpCharsetRange] // cache
}
final case class `Accept-Charset`(charsetRanges: immutable.Seq[HttpCharsetRange]) extends jm.headers.AcceptCharset
    with RequestHeader {
  require(charsetRanges.nonEmpty, "charsetRanges must not be empty")
  import `Accept-Charset`.charsetRangesRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ charsetRanges
  protected def companion = `Accept-Charset`

  /** Java API */
  def getCharsetRanges: Iterable[jm.HttpCharsetRange] = charsetRanges.asJava
}

// https://tools.ietf.org/html/rfc7231#section-5.3.4
object `Accept-Encoding` extends ModeledCompanion[`Accept-Encoding`] {
  @pre213
  def apply(encodings: HttpEncodingRange*): `Accept-Encoding` =
    apply(immutable.Seq(encodings: _*))
  @since213
  def apply(): `Accept-Encoding` =
    apply(immutable.Seq.empty)
  @since213
  def apply(firstEncoding: HttpEncodingRange, otherEncodings: HttpEncodingRange*): `Accept-Encoding` =
    apply(firstEncoding +: otherEncodings)
  implicit val encodingsRenderer: Renderer[immutable.Iterable[HttpEncodingRange]] =
    Renderer.defaultSeqRenderer[HttpEncodingRange] // cache
}
final case class `Accept-Encoding`(encodings: immutable.Seq[HttpEncodingRange]) extends jm.headers.AcceptEncoding
    with RequestHeader {
  import `Accept-Encoding`.encodingsRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ encodings
  protected def companion = `Accept-Encoding`

  /** Java API */
  def getEncodings: Iterable[jm.headers.HttpEncodingRange] = encodings.asJava
}

// https://tools.ietf.org/html/rfc7231#section-5.3.5
object `Accept-Language` extends ModeledCompanion[`Accept-Language`] {
  def apply(first: LanguageRange, more: LanguageRange*): `Accept-Language` = apply(immutable.Seq(first +: more: _*))
  implicit val languagesRenderer: Renderer[immutable.Iterable[LanguageRange]] =
    Renderer.defaultSeqRenderer[LanguageRange] // cache
}
final case class `Accept-Language`(languages: immutable.Seq[LanguageRange]) extends jm.headers.AcceptLanguage
    with RequestHeader {
  require(languages.nonEmpty, "languages must not be empty")
  import `Accept-Language`.languagesRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ languages
  protected def companion = `Accept-Language`

  /** Java API */
  def getLanguages: Iterable[jm.headers.LanguageRange] = languages.asJava
}

// https://tools.ietf.org/html/rfc7233#section-2.3
object `Accept-Ranges` extends ModeledCompanion[`Accept-Ranges`] {
  @pre213
  def apply(rangeUnits: RangeUnit*): `Accept-Ranges` =
    apply(immutable.Seq(rangeUnits: _*))
  @since213
  def apply(): `Accept-Ranges` =
    apply(immutable.Seq.empty)
  @since213
  def apply(firstRangeUnit: RangeUnit, otherRangeUnits: RangeUnit*): `Accept-Ranges` =
    apply(firstRangeUnit +: otherRangeUnits)
  implicit val rangeUnitsRenderer: Renderer[immutable.Iterable[RangeUnit]] = Renderer.defaultSeqRenderer[RangeUnit] // cache
}
final case class `Accept-Ranges`(rangeUnits: immutable.Seq[RangeUnit]) extends jm.headers.AcceptRanges
    with ResponseHeader {
  import `Accept-Ranges`.rangeUnitsRenderer
  def renderValue[R <: Rendering](r: R): r.type = if (rangeUnits.isEmpty) r ~~ "none" else r ~~ rangeUnits
  protected def companion = `Accept-Ranges`

  /** Java API */
  def getRangeUnits: Iterable[jm.headers.RangeUnit] = rangeUnits.asJava
}

// https://www.w3.org/TR/cors/#access-control-allow-credentials-response-header
object `Access-Control-Allow-Credentials` extends ModeledCompanion[`Access-Control-Allow-Credentials`]
final case class `Access-Control-Allow-Credentials`(allow: Boolean)
    extends jm.headers.AccessControlAllowCredentials with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ allow.toString
  protected def companion = `Access-Control-Allow-Credentials`
}

// https://www.w3.org/TR/cors/#access-control-allow-headers-response-header
object `Access-Control-Allow-Headers` extends ModeledCompanion[`Access-Control-Allow-Headers`] {
  @pre213
  def apply(headers: String*): `Access-Control-Allow-Headers` =
    apply(immutable.Seq(headers: _*))
  @since213
  def apply(firstHeader: String, otherHeaders: String*): `Access-Control-Allow-Headers` =
    apply(firstHeader +: otherHeaders)
  implicit val headersRenderer: Renderer[immutable.Iterable[String]] = Renderer.defaultSeqRenderer[String] // cache
}
final case class `Access-Control-Allow-Headers`(headers: immutable.Seq[String])
    extends jm.headers.AccessControlAllowHeaders with ResponseHeader {
  import `Access-Control-Allow-Headers`.headersRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ headers
  protected def companion = `Access-Control-Allow-Headers`

  /** Java API */
  def getHeaders: Iterable[String] = headers.asJava
}

// https://www.w3.org/TR/cors/#access-control-allow-methods-response-header
object `Access-Control-Allow-Methods` extends ModeledCompanion[`Access-Control-Allow-Methods`] {
  @pre213
  def apply(methods: HttpMethod*): `Access-Control-Allow-Methods` =
    apply(immutable.Seq(methods: _*))
  @since213
  def apply(firstMethod: HttpMethod, otherMethods: HttpMethod*): `Access-Control-Allow-Methods` =
    apply(firstMethod +: otherMethods)
  implicit val methodsRenderer: Renderer[immutable.Iterable[HttpMethod]] = Renderer.defaultSeqRenderer[HttpMethod] // cache
}
final case class `Access-Control-Allow-Methods`(methods: immutable.Seq[HttpMethod])
    extends jm.headers.AccessControlAllowMethods with ResponseHeader {
  import `Access-Control-Allow-Methods`.methodsRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ methods
  protected def companion = `Access-Control-Allow-Methods`

  /** Java API */
  def getMethods: Iterable[jm.HttpMethod] = methods.asJava
}

// https://www.w3.org/TR/cors/#access-control-allow-origin-response-header
object `Access-Control-Allow-Origin` extends ModeledCompanion[`Access-Control-Allow-Origin`] {
  val `*` = forRange(HttpOriginRange.`*`)
  val `null` = forRange(HttpOriginRange())
  def apply(origin: HttpOrigin) = forRange(HttpOriginRange(origin))

  /**
   * Creates an `Access-Control-Allow-Origin` header for the given origin range.
   *
   * CAUTION: Even though allowed by the spec (https://www.w3.org/TR/cors/#access-control-allow-origin-response-header)
   * `Access-Control-Allow-Origin` headers with more than a single origin appear to be largely unsupported in the field.
   * Make sure to thoroughly test such usages with all expected clients!
   */
  def forRange(range: HttpOriginRange) = new `Access-Control-Allow-Origin`(range)
}
final case class `Access-Control-Allow-Origin` private (range: HttpOriginRange)
    extends jm.headers.AccessControlAllowOrigin with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ range
  protected def companion = `Access-Control-Allow-Origin`
}

// https://www.w3.org/TR/cors/#access-control-expose-headers-response-header
object `Access-Control-Expose-Headers` extends ModeledCompanion[`Access-Control-Expose-Headers`] {
  @pre213
  def apply(headers: String*): `Access-Control-Expose-Headers` =
    apply(immutable.Seq(headers: _*))
  @since213
  def apply(firstHeader: String, otherHeaders: String*): `Access-Control-Expose-Headers` =
    apply(firstHeader +: otherHeaders)
  implicit val headersRenderer: Renderer[immutable.Iterable[String]] = Renderer.defaultSeqRenderer[String] // cache
}
final case class `Access-Control-Expose-Headers`(headers: immutable.Seq[String])
    extends jm.headers.AccessControlExposeHeaders with ResponseHeader {
  import `Access-Control-Expose-Headers`.headersRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ headers
  protected def companion = `Access-Control-Expose-Headers`

  /** Java API */
  def getHeaders: Iterable[String] = headers.asJava
}

// https://www.w3.org/TR/cors/#access-control-max-age-response-header
object `Access-Control-Max-Age` extends ModeledCompanion[`Access-Control-Max-Age`]
final case class `Access-Control-Max-Age`(deltaSeconds: Long) extends jm.headers.AccessControlMaxAge
    with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ deltaSeconds
  protected def companion = `Access-Control-Max-Age`
}

// https://www.w3.org/TR/cors/#access-control-request-headers-request-header
object `Access-Control-Request-Headers` extends ModeledCompanion[`Access-Control-Request-Headers`] {
  @pre213
  def apply(headers: String*): `Access-Control-Request-Headers` =
    apply(immutable.Seq(headers: _*))
  @since213
  def apply(firstHeader: String, otherHeaders: String*): `Access-Control-Request-Headers` =
    apply(firstHeader +: otherHeaders)
  implicit val headersRenderer: Renderer[immutable.Iterable[String]] = Renderer.defaultSeqRenderer[String] // cache
}
final case class `Access-Control-Request-Headers`(headers: immutable.Seq[String])
    extends jm.headers.AccessControlRequestHeaders with RequestHeader {
  import `Access-Control-Request-Headers`.headersRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ headers
  protected def companion = `Access-Control-Request-Headers`

  /** Java API */
  def getHeaders: Iterable[String] = headers.asJava
}

// https://www.w3.org/TR/cors/#access-control-request-method-request-header
object `Access-Control-Request-Method` extends ModeledCompanion[`Access-Control-Request-Method`]
final case class `Access-Control-Request-Method`(method: HttpMethod) extends jm.headers.AccessControlRequestMethod
    with RequestHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ method
  protected def companion = `Access-Control-Request-Method`
}

// https://tools.ietf.org/html/rfc7234#section-5.1
object Age extends ModeledCompanion[Age]
final case class Age(deltaSeconds: Long) extends jm.headers.Age with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ deltaSeconds
  protected def companion = Age
}

// https://tools.ietf.org/html/rfc7231#section-7.4.1
object Allow extends ModeledCompanion[Allow] {
  @pre213
  def apply(methods: HttpMethod*): Allow =
    apply(immutable.Seq(methods: _*))
  @since213
  def apply(): `Allow` =
    apply(immutable.Seq.empty)
  @since213
  def apply(firstMethod: HttpMethod, otherMethods: HttpMethod*): Allow =
    apply(firstMethod +: otherMethods)
  implicit val methodsRenderer: Renderer[immutable.Iterable[HttpMethod]] = Renderer.defaultSeqRenderer[HttpMethod] // cache
}
final case class Allow(methods: immutable.Seq[HttpMethod]) extends jm.headers.Allow with ResponseHeader {
  import Allow.methodsRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ methods
  protected def companion = Allow

  /** Java API */
  def getMethods: Iterable[jm.HttpMethod] = methods.asJava
}

// https://tools.ietf.org/html/rfc7235#section-4.2
object Authorization extends ModeledCompanion[Authorization]
final case class Authorization(credentials: HttpCredentials) extends jm.headers.Authorization with RequestHeader
    with SensitiveHttpHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ credentials
  protected def companion = Authorization
}

// https://tools.ietf.org/html/rfc7234#section-5.2
object `Cache-Control` extends ModeledCompanion[`Cache-Control`] {
  def apply(first: CacheDirective, more: CacheDirective*): `Cache-Control` = apply(immutable.Seq(first +: more: _*))
  implicit val directivesRenderer: Renderer[immutable.Iterable[CacheDirective]] =
    Renderer.defaultSeqRenderer[CacheDirective] // cache
}
final case class `Cache-Control`(directives: immutable.Seq[CacheDirective]) extends jm.headers.CacheControl
    with RequestResponseHeader {
  require(directives.nonEmpty, "directives must not be empty")
  import `Cache-Control`.directivesRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ directives
  protected def companion = `Cache-Control`

  /** Java API */
  def getDirectives: Iterable[jm.headers.CacheDirective] = directives.asJava
}

// https://tools.ietf.org/html/rfc7230#section-6.1
object Connection extends ModeledCompanion[Connection] {
  def apply(first: String, more: String*): Connection = apply(immutable.Seq(first +: more: _*))
  implicit val tokensRenderer: Renderer[immutable.Iterable[String]] = Renderer.defaultSeqRenderer[String] // cache
}
final case class Connection(tokens: immutable.Seq[String]) extends jm.headers.Connection
    with RequestResponseHeader {
  require(tokens.nonEmpty, "tokens must not be empty")
  import Connection.tokensRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ tokens
  def hasClose = has("close")
  def hasKeepAlive = has("keep-alive")
  def hasUpgrade = has("upgrade")
  def append(tokens: immutable.Seq[String]) = Connection(this.tokens ++ tokens)
  @tailrec private def has(item: String, ix: Int = 0): Boolean =
    if (ix < tokens.length)
      if (tokens(ix).equalsIgnoreCase(item)) true
      else has(item, ix + 1)
    else false
  protected def companion = Connection

  /** Java API */
  def getTokens: Iterable[String] = tokens.asJava
}

// https://tools.ietf.org/html/rfc7230#section-3.3.2
object `Content-Length` extends ModeledCompanion[`Content-Length`]

/**
 * Instances of this class will only be created transiently during header parsing and will never appear
 * in HttpMessage.header. To access the Content-Length, see subclasses of HttpEntity.
 */
final case class `Content-Length` private[pekko] (length: Long) extends jm.headers.ContentLength
    with RequestResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ length
  protected def companion = `Content-Length`
}

// https://tools.ietf.org/html/rfc7231#section-3.1.4.2
object `Content-Location` extends ModeledCompanion[`Content-Location`]
final case class `Content-Location`(uri: Uri) extends jm.headers.ContentLocation with ResponseHeader {
  require(uri.fragment.isEmpty, "Content-Location header URI must not contain a fragment")
  require(uri.authority.userinfo.isEmpty, "Content-Location header URI must not contain a userinfo component")

  def renderValue[R <: Rendering](r: R): r.type = { import UriRendering.UriRenderer; r ~~ uri }
  protected def companion = `Content-Location`

  /** Java API */
  def getUri: pekko.http.javadsl.model.Uri = uri.asJava
}

/**
 * Document https://tools.ietf.org/html/rfc6266 updates document https://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html . Between these
 * two there is slight but important difference regarding how parameter values are formatted. In RFC6266 parameters values are without quotes and
 * in RFC2616 they are quoted. Since common practice among http servers is to understand quoted values, we use older document
 * as reference here.
 * Extended (i.e. encoded) parameter values are not quoted, see ext-parameter - https://tools.ietf.org/html/rfc5987#section-3.2.1
 *
 * Note: Pekko HTTP uses "filename" key to store filename and handles encoding/decoding automatically.
 * To output customized ASCII fallback version of filename provide "filename*" for unicode and "filename" for ASCII.
 */
object `Content-Disposition` extends ModeledCompanion[`Content-Disposition`]
final case class `Content-Disposition`(dispositionType: ContentDispositionType, params: Map[String, String] = Map.empty)
    extends jm.headers.ContentDisposition with RequestResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = {
    import Rendering.{ contentDispositionFilenameSafeChars => safeChars }
    r ~~ dispositionType
    val renderExtFilename =
      params.get("filename").exists(!safeChars.matchesAll(_))
    val withExtParams =
      if (renderExtFilename && !params.contains("filename*"))
        params + ("filename*" -> params("filename"))
      else params
    // it is advised that "filename" should occur first - https://tools.ietf.org/html/rfc6266#appendix-D
    val withExtParamsSorted =
      if (withExtParams.contains("filename") && withExtParams.contains("filename*"))
        TreeMap[String, String]() ++ withExtParams
      else withExtParams
    withExtParamsSorted.foreach {
      case (k, v) if k == "filename" =>
        r ~~ "; " ~~ k ~~ '=' ~~ '"'
        r.putReplaced(v, keep = safeChars, placeholder = '?') ~~ '"'
      case (k, v) if k.endsWith("*") =>
        r ~~ "; " ~~ k ~~ '=' ~~ "UTF-8''"
        UriRendering.encode(r, v, UTF8, keep = `attr-char`, replaceSpaces = false)
      case (k, v) => r ~~ "; " ~~ k ~~ '=' ~~#! v
    }
    r
  }
  protected def companion = `Content-Disposition`

  /** Java API */
  def getParams: util.Map[String, String] = params.asJava
}

// https://tools.ietf.org/html/rfc7231#section-3.1.2.2
object `Content-Encoding` extends ModeledCompanion[`Content-Encoding`] {
  def apply(first: HttpEncoding, more: HttpEncoding*): `Content-Encoding` = apply(immutable.Seq(first +: more: _*))
  implicit val encodingsRenderer: Renderer[immutable.Iterable[HttpEncoding]] = Renderer.defaultSeqRenderer[HttpEncoding] // cache
}
final case class `Content-Encoding`(encodings: immutable.Seq[HttpEncoding]) extends jm.headers.ContentEncoding
    with RequestResponseHeader {
  require(encodings.nonEmpty, "encodings must not be empty")
  import `Content-Encoding`.encodingsRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ encodings
  protected def companion = `Content-Encoding`

  /** Java API */
  def getEncodings: Iterable[jm.headers.HttpEncoding] = encodings.asJava
}

// https://tools.ietf.org/html/rfc7233#section-4.2
object `Content-Range` extends ModeledCompanion[`Content-Range`] {
  def apply(byteContentRange: ByteContentRange): `Content-Range` = apply(RangeUnits.Bytes, byteContentRange)
}
final case class `Content-Range`(rangeUnit: RangeUnit, contentRange: ContentRange) extends jm.headers.ContentRange
    with RequestResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ rangeUnit ~~ ' ' ~~ contentRange
  protected def companion = `Content-Range`
}

// https://tools.ietf.org/html/rfc7231#section-3.1.1.5
object `Content-Type` extends ModeledCompanion[`Content-Type`]

/**
 * Instances of this class will only be created transiently during header parsing and will never appear
 * in HttpMessage.header. To access the Content-Type, see subclasses of HttpEntity.
 */
final case class `Content-Type` private[pekko] (contentType: ContentType) extends jm.headers.ContentType
    with RequestResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ contentType
  protected def companion = `Content-Type`
}

// https://tools.ietf.org/html/rfc6265#section-4.2
object Cookie extends ModeledCompanion[Cookie] {
  def apply(first: HttpCookiePair, more: HttpCookiePair*): Cookie = apply(immutable.Seq(first +: more: _*))
  def apply(name: String, value: String): Cookie = apply(HttpCookiePair(name, value))
  @pre213
  def apply(values: (String, String)*): Cookie = apply(values.map(HttpCookiePair(_)).toList)
  @since213
  def apply(first: (String, String), more: (String, String)*): Cookie = apply((first +: more).map(HttpCookiePair(_)))
  implicit val cookiePairsRenderer: Renderer[immutable.Iterable[HttpCookiePair]] =
    Renderer.seqRenderer[HttpCookiePair](separator = "; ") // cache
}
final case class Cookie(cookies: immutable.Seq[HttpCookiePair]) extends jm.headers.Cookie with RequestHeader
    with SensitiveHttpHeader {
  require(cookies.nonEmpty, "cookies must not be empty")
  import Cookie.cookiePairsRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ cookies
  protected def companion = Cookie

  /** Java API */
  def getCookies: Iterable[jm.headers.HttpCookiePair] = cookies.asJava
}

// https://tools.ietf.org/html/rfc7231#section-7.1.1.2
object Date extends ModeledCompanion[Date]
final case class Date(date: DateTime) extends jm.headers.Date with RequestResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
  protected def companion = Date
}

/**
 * INTERNAL API
 */
@InternalApi
private[headers] object EmptyCompanion extends ModeledCompanion[EmptyHeader.type]

/**
 * INTERNAL API
 */
@InternalApi
private[http] object EmptyHeader extends SyntheticHeader {
  def renderValue[R <: Rendering](r: R): r.type = r
  protected def companion: ModeledCompanion[EmptyHeader.type] = EmptyCompanion
}

// https://tools.ietf.org/html/rfc7232#section-2.3
object ETag extends ModeledCompanion[ETag] {
  def apply(tag: String, weak: Boolean = false): ETag = ETag(EntityTag(tag, weak))

}
final case class ETag(etag: EntityTag) extends jm.headers.ETag with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ etag
  protected def companion = ETag
}

// https://tools.ietf.org/html/rfc7231#section-5.1.1
object Expect extends ModeledCompanion[Expect] {
  val `100-continue` = new Expect() {}
}
sealed abstract case class Expect private () extends RequestHeader {
  final def renderValue[R <: Rendering](r: R): r.type = r ~~ "100-continue"
  protected def companion = Expect
}

// https://tools.ietf.org/html/rfc7234#section-5.3
object Expires extends ModeledCompanion[Expires]
final case class Expires(date: DateTime) extends jm.headers.Expires with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
  protected def companion = Expires
}

// https://tools.ietf.org/html/rfc7230#section-5.4
object Host extends ModeledCompanion[Host] {
  def apply(authority: Uri.Authority): Host = apply(authority.host, authority.port)
  def apply(address: InetSocketAddress): Host = apply(address.getHostString, address.getPort)
  def apply(host: String): Host = apply(host, 0)
  def apply(host: String, port: Int): Host = apply(Uri.Host(host), port)
  val empty = Host("")
}
final case class Host(host: Uri.Host, port: Int = 0) extends jm.headers.Host with RequestHeader
    with SensitiveHttpHeader {
  import UriRendering.HostRenderer
  require((port >> 16) == 0, "Illegal port: " + port)
  def isEmpty = host.isEmpty
  def renderValue[R <: Rendering](r: R): r.type = if (port > 0) r ~~ host ~~ ':' ~~ port else r ~~ host
  protected def companion = Host
  def equalsIgnoreCase(other: Host): Boolean = host.equalsIgnoreCase(other.host) && port == other.port
}

// https://tools.ietf.org/html/rfc7232#section-3.1
object `If-Match` extends ModeledCompanion[`If-Match`] {
  val `*` = `If-Match`(EntityTagRange.`*`)
  def apply(first: EntityTag, more: EntityTag*): `If-Match` =
    `If-Match`(EntityTagRange(first +: more: _*))
}
final case class `If-Match`(m: EntityTagRange) extends jm.headers.IfMatch with RequestHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ m
  protected def companion = `If-Match`
}

// https://tools.ietf.org/html/rfc7232#section-3.3
object `If-Modified-Since` extends ModeledCompanion[`If-Modified-Since`]
final case class `If-Modified-Since`(date: DateTime) extends jm.headers.IfModifiedSince with RequestHeader {
  def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
  protected def companion = `If-Modified-Since`
}

// https://tools.ietf.org/html/rfc7232#section-3.2
object `If-None-Match` extends ModeledCompanion[`If-None-Match`] {
  val `*` = `If-None-Match`(EntityTagRange.`*`)
  def apply(first: EntityTag, more: EntityTag*): `If-None-Match` =
    `If-None-Match`(EntityTagRange(first +: more: _*))
}
final case class `If-None-Match`(m: EntityTagRange) extends jm.headers.IfNoneMatch with RequestHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ m
  protected def companion = `If-None-Match`
}

// https://tools.ietf.org/html/rfc7233#section-3.2
object `If-Range` extends ModeledCompanion[`If-Range`] {
  def apply(tag: EntityTag): `If-Range` = apply(Left(tag))
  def apply(timestamp: DateTime): `If-Range` = apply(Right(timestamp))
}
final case class `If-Range`(entityTagOrDateTime: Either[EntityTag, DateTime]) extends RequestHeader {
  def renderValue[R <: Rendering](r: R): r.type =
    entityTagOrDateTime match {
      case Left(tag)       => r ~~ tag
      case Right(dateTime) => dateTime.renderRfc1123DateTimeString(r)
    }
  protected def companion = `If-Range`
}

// https://tools.ietf.org/html/rfc7232#section-3.4
object `If-Unmodified-Since` extends ModeledCompanion[`If-Unmodified-Since`]
final case class `If-Unmodified-Since`(date: DateTime) extends jm.headers.IfUnmodifiedSince with RequestHeader {
  def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
  protected def companion = `If-Unmodified-Since`
}

// https://www.w3.org/TR/eventsource/#last-event-id
object `Last-Event-ID` extends ModeledCompanion[`Last-Event-ID`]
final case class `Last-Event-ID`(id: String) extends jm.headers.LastEventId with RequestHeader {
  override protected[http] def renderValue[R <: Rendering](r: R): r.type = r ~~ id
  override protected def companion = `Last-Event-ID`
}

// https://tools.ietf.org/html/rfc7232#section-2.2
object `Last-Modified` extends ModeledCompanion[`Last-Modified`]
final case class `Last-Modified`(date: DateTime) extends jm.headers.LastModified with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = date.renderRfc1123DateTimeString(r)
  protected def companion = `Last-Modified`
}

// https://tools.ietf.org/html/rfc5988#section-5
object Link extends ModeledCompanion[Link] {
  def apply(uri: Uri, first: LinkParam, more: LinkParam*): Link =
    apply(immutable.Seq(LinkValue(uri, first +: more.toList)))
  @pre213
  def apply(values: LinkValue*): Link = apply(immutable.Seq(values: _*))
  @since213
  def apply(firstValue: LinkValue, otherValues: LinkValue*): Link = apply(firstValue +: otherValues)

  implicit val valuesRenderer: Renderer[immutable.Iterable[LinkValue]] = Renderer.defaultSeqRenderer[LinkValue] // cache
}
final case class Link(values: immutable.Seq[LinkValue]) extends jm.headers.Link with RequestResponseHeader {
  import Link.valuesRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ values
  protected def companion = Link

  /** Java API */
  def getValues: Iterable[jm.headers.LinkValue] = values.asJava
}

// https://tools.ietf.org/html/rfc7231#section-7.1.2
object Location extends ModeledCompanion[Location]
final case class Location(uri: Uri) extends jm.headers.Location with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = { import UriRendering.UriRenderer; r ~~ uri }
  protected def companion = Location

  /** Java API */
  def getUri: pekko.http.javadsl.model.Uri = uri.asJava
}

// https://tools.ietf.org/html/rfc6454#section-7
object Origin extends ModeledCompanion[Origin] {
  @pre213
  def apply(origins: HttpOrigin*): Origin = apply(immutable.Seq(origins: _*))
  @since213
  def apply(firstOrigin: HttpOrigin, otherOrigins: HttpOrigin*): Origin = apply(firstOrigin +: otherOrigins)
}
final case class Origin(origins: immutable.Seq[HttpOrigin]) extends jm.headers.Origin with RequestHeader
    with SensitiveHttpHeader {

  def renderValue[R <: Rendering](r: R): r.type = if (origins.isEmpty) r ~~ "null" else r ~~ origins
  protected def companion = Origin

  /** Java API */
  def getOrigins: Iterable[jm.headers.HttpOrigin] = origins.asJava
}

// https://tools.ietf.org/html/rfc7235#section-4.3
object `Proxy-Authenticate` extends ModeledCompanion[`Proxy-Authenticate`] {
  def apply(first: HttpChallenge, more: HttpChallenge*): `Proxy-Authenticate` = apply(immutable.Seq(first +: more: _*))
  implicit val challengesRenderer: Renderer[immutable.Iterable[HttpChallenge]] =
    Renderer.defaultSeqRenderer[HttpChallenge] // cache
}
final case class `Proxy-Authenticate`(challenges: immutable.Seq[HttpChallenge]) extends jm.headers.ProxyAuthenticate
    with ResponseHeader {
  require(challenges.nonEmpty, "challenges must not be empty")
  import `Proxy-Authenticate`.challengesRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ challenges
  protected def companion = `Proxy-Authenticate`

  /** Java API */
  def getChallenges: Iterable[jm.headers.HttpChallenge] = challenges.asJava
}

// https://tools.ietf.org/html/rfc7235#section-4.4
object `Proxy-Authorization` extends ModeledCompanion[`Proxy-Authorization`]
final case class `Proxy-Authorization`(credentials: HttpCredentials) extends jm.headers.ProxyAuthorization
    with RequestHeader with SensitiveHttpHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ credentials
  protected def companion = `Proxy-Authorization`
}

// https://tools.ietf.org/html/rfc7233#section-3.1
object Range extends ModeledCompanion[Range] {
  def apply(first: ByteRange, more: ByteRange*): Range = apply(immutable.Seq(first +: more: _*))
  def apply(ranges: immutable.Seq[ByteRange]): Range = Range(RangeUnits.Bytes, ranges)
  implicit val rangesRenderer: Renderer[immutable.Iterable[ByteRange]] = Renderer.defaultSeqRenderer[ByteRange] // cache
}
final case class Range(rangeUnit: RangeUnit, ranges: immutable.Seq[ByteRange]) extends jm.headers.Range
    with RequestHeader {
  require(ranges.nonEmpty, "ranges must not be empty")
  import Range.rangesRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ rangeUnit ~~ '=' ~~ ranges
  protected def companion = Range

  /** Java API */
  def getRanges: Iterable[jm.headers.ByteRange] = ranges.asJava
}

final case class RawHeader(name: String, value: String) extends jm.headers.RawHeader {
  if (name == null) throw new IllegalArgumentException("name must not be null")
  if (value == null) throw new IllegalArgumentException("value must not be null")
  def renderInRequests = true
  def renderInResponses = true
  val lowercaseName = name.toRootLowerCase
  def render[R <: Rendering](r: R): r.type = r ~~ name ~~ ':' ~~ ' ' ~~ value
}
object RawHeader {
  def unapply[H <: HttpHeader](customHeader: H): Option[(String, String)] =
    Some(customHeader.name -> customHeader.value)
}

object `Raw-Request-URI` extends ModeledCompanion[`Raw-Request-URI`]
final case class `Raw-Request-URI`(uri: String) extends jm.headers.RawRequestURI with SyntheticHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ uri
  protected def companion = `Raw-Request-URI`
}

@deprecated("use remote-address-attribute instead", since = "Akka HTTP 10.2.0")
object `Remote-Address` extends ModeledCompanion[`Remote-Address`]
@deprecated("use remote-address-attribute instead", since = "Akka HTTP 10.2.0")
final case class `Remote-Address`(address: RemoteAddress) extends jm.headers.RemoteAddress with SyntheticHeader
    with SensitiveHttpHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ address
  protected def companion = `Remote-Address`
}

// https://tools.ietf.org/html/rfc7231#section-5.5.2
object Referer extends ModeledCompanion[Referer]
final case class Referer(uri: Uri) extends jm.headers.Referer with RequestHeader with SensitiveHttpHeader {
  require(uri.fragment.isEmpty, "Referer header URI must not contain a fragment")
  require(uri.authority.userinfo.isEmpty, "Referer header URI must not contain a userinfo component")

  def renderValue[R <: Rendering](r: R): r.type = { import UriRendering.UriRenderer; r ~~ uri }
  protected def companion = Referer

  /** Java API */
  def getUri: pekko.http.javadsl.model.Uri = uri.asJava
}

object `Retry-After` extends ModeledCompanion[`Retry-After`] {
  def apply(delaySeconds: Long): `Retry-After` = apply(RetryAfterDuration(delaySeconds))
  def apply(timestamp: DateTime): `Retry-After` = apply(RetryAfterDateTime(timestamp))
}

final case class `Retry-After`(delaySecondsOrDateTime: RetryAfterParameter) extends jm.headers.RetryAfter
    with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = delaySecondsOrDateTime match {
    case RetryAfterDuration(delay)    => r ~~ delay
    case RetryAfterDateTime(dateTime) => dateTime.renderRfc1123DateTimeString(r)
  }

  protected def companion = `Retry-After`

  /** Java API suppport */
  override protected def delaySeconds(): Option[java.lang.Long] = PartialFunction.condOpt(delaySecondsOrDateTime) {
    case RetryAfterDuration(delay) => Long.box(delay)
  }
  override protected def dateTime(): Option[DateTime] = PartialFunction.condOpt(delaySecondsOrDateTime) {
    case RetryAfterDateTime(dateTime) => dateTime
  }
}

/**
 * INTERNAL API
 */
// https://tools.ietf.org/html/rfc6455#section-4.3
@InternalApi
private[http] object `Sec-WebSocket-Accept` extends ModeledCompanion[`Sec-WebSocket-Accept`] {
  // Defined at https://tools.ietf.org/html/rfc6455#section-4.2.2
  val MagicGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

  /** Generates the matching accept header for this key */
  def forKey(key: `Sec-WebSocket-Key`): `Sec-WebSocket-Accept` = {
    val sha1 = MessageDigest.getInstance("sha1")
    val salted = key.key + MagicGuid
    val hash = sha1.digest(salted.asciiBytes)
    val acceptKey = Base64.rfc2045().encodeToString(hash, false)
    `Sec-WebSocket-Accept`(acceptKey)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[http] final case class `Sec-WebSocket-Accept`(key: String) extends ResponseHeader {
  protected[http] def renderValue[R <: Rendering](r: R): r.type = r ~~ key

  protected def companion = `Sec-WebSocket-Accept`
}

/**
 * INTERNAL API
 */
// https://tools.ietf.org/html/rfc6455#section-4.3
@InternalApi
private[http] object `Sec-WebSocket-Extensions` extends ModeledCompanion[`Sec-WebSocket-Extensions`] {
  implicit val extensionsRenderer: Renderer[immutable.Iterable[WebSocketExtension]] =
    Renderer.defaultSeqRenderer[WebSocketExtension]
}

/**
 * INTERNAL API
 */
@InternalApi
private[http] final case class `Sec-WebSocket-Extensions`(extensions: immutable.Seq[WebSocketExtension])
    extends RequestResponseHeader {
  require(extensions.nonEmpty, "Sec-WebSocket-Extensions.extensions must not be empty")
  import `Sec-WebSocket-Extensions`.extensionsRenderer
  protected[http] def renderValue[R <: Rendering](r: R): r.type = r ~~ extensions
  protected def companion = `Sec-WebSocket-Extensions`
}

// https://tools.ietf.org/html/rfc6455#section-4.3
/**
 * INTERNAL API
 */
@InternalApi
private[http] object `Sec-WebSocket-Key` extends ModeledCompanion[`Sec-WebSocket-Key`] {
  def apply(keyBytes: Array[Byte]): `Sec-WebSocket-Key` = {
    require(keyBytes.length == 16, s"Sec-WebSocket-Key keyBytes must have length 16 but had ${keyBytes.length}")
    `Sec-WebSocket-Key`(Base64.rfc2045().encodeToString(keyBytes, false))
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[http] final case class `Sec-WebSocket-Key`(key: String) extends RequestHeader {
  protected[http] def renderValue[R <: Rendering](r: R): r.type = r ~~ key

  protected def companion = `Sec-WebSocket-Key`

  /**
   * Checks if the key value is valid according to the WebSocket specification, i.e.
   * if the String is a Base64 representation of 16 bytes.
   */
  def isValid: Boolean = Try(Base64.rfc2045().decode(key.toCharArray)).toOption.exists(_.length == 16)
}

// https://tools.ietf.org/html/rfc6455#section-4.3
/**
 * INTERNAL API
 */
@InternalApi
private[http] object `Sec-WebSocket-Protocol` extends ModeledCompanion[`Sec-WebSocket-Protocol`] {
  implicit val protocolsRenderer: Renderer[immutable.Iterable[String]] = Renderer.defaultSeqRenderer[String]
}

/**
 * INTERNAL API
 */
@InternalApi
private[http] final case class `Sec-WebSocket-Protocol`(protocols: immutable.Seq[String])
    extends jm.headers.SecWebSocketProtocol with RequestResponseHeader {
  require(protocols.nonEmpty, "Sec-WebSocket-Protocol.protocols must not be empty")
  import `Sec-WebSocket-Protocol`.protocolsRenderer
  protected[http] def renderValue[R <: Rendering](r: R): r.type = r ~~ protocols
  protected def companion = `Sec-WebSocket-Protocol`

  /** Java API */
  override def getProtocols: Iterable[String] = protocols.asJava
}

// https://tools.ietf.org/html/rfc6455#section-4.3
/**
 * INTERNAL API
 */
@InternalApi
private[http] object `Sec-WebSocket-Version` extends ModeledCompanion[`Sec-WebSocket-Version`] {
  implicit val versionsRenderer: Renderer[immutable.Iterable[Int]] = Renderer.defaultSeqRenderer[Int]
}

/**
 * INTERNAL API
 */
@InternalApi
private[http] final case class `Sec-WebSocket-Version`(versions: immutable.Seq[Int])
    extends RequestResponseHeader {
  require(versions.nonEmpty, "Sec-WebSocket-Version.versions must not be empty")
  require(versions.forall(v => v >= 0 && v <= 255),
    s"Sec-WebSocket-Version.versions must be in the range 0 <= version <= 255 but were $versions")
  import `Sec-WebSocket-Version`.versionsRenderer
  protected[http] def renderValue[R <: Rendering](r: R): r.type = r ~~ versions
  def hasVersion(versionNumber: Int): Boolean = versions contains versionNumber
  protected def companion = `Sec-WebSocket-Version`
}

// https://tools.ietf.org/html/rfc7231#section-7.4.2
object Server extends ModeledCompanion[Server] {
  def apply(products: String): Server = apply(ProductVersion.parseMultiple(products))
  def apply(first: ProductVersion, more: ProductVersion*): Server = apply(immutable.Seq(first +: more: _*))
  implicit val productsRenderer: Renderer[immutable.Iterable[ProductVersion]] =
    Renderer.seqRenderer[ProductVersion](separator = " ") // cache
}
final case class Server(products: immutable.Seq[ProductVersion]) extends jm.headers.Server with ResponseHeader {
  require(products.nonEmpty, "products must not be empty")
  import Server.productsRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ products
  protected def companion = Server

  /** Java API */
  def getProducts: Iterable[jm.headers.ProductVersion] = products.asJava
}

// https://tools.ietf.org/html/rfc6797
object `Strict-Transport-Security` extends ModeledCompanion[`Strict-Transport-Security`] {
  def apply(maxAge: Long, includeSubDomains: Option[Boolean]) =
    new `Strict-Transport-Security`(maxAge, includeSubDomains.getOrElse(false))

  private val maxAges: PartialFunction[StrictTransportSecurityDirective, MaxAge] = { case m: MaxAge => m }
  private val isIncludeSubDomains: StrictTransportSecurityDirective => Boolean = { _ eq IncludeSubDomains }
  def fromDirectives(directives: StrictTransportSecurityDirective*) = {
    val maxAgeDirectives = directives.collect(maxAges)
    if (maxAgeDirectives.size != 1) throw new IllegalArgumentException("exactly one 'max-age' directive required")

    val includeSubDomainsDirectivesCount = directives.count(isIncludeSubDomains)
    if (includeSubDomainsDirectivesCount > 1)
      throw new IllegalArgumentException("at most one 'includeSubDomains' directive allowed")

    new `Strict-Transport-Security`(maxAgeDirectives.head.value, includeSubDomainsDirectivesCount == 1)
  }
}
final case class `Strict-Transport-Security`(
    maxAge: Long, includeSubDomains: Boolean = false) extends jm.headers.StrictTransportSecurity with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = {
    r ~~ "max-age=" ~~ maxAge
    if (includeSubDomains) r ~~ "; includeSubDomains"
    r
  }
  protected def companion = `Strict-Transport-Security`
}

// https://tools.ietf.org/html/rfc6265
object `Set-Cookie` extends ModeledCompanion[`Set-Cookie`]
final case class `Set-Cookie`(cookie: HttpCookie) extends jm.headers.SetCookie with ResponseHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ cookie
  protected def companion = `Set-Cookie`
}

// https://tools.ietf.org/html/rfc7230#section-4.3
object TE extends ModeledCompanion[TE] {
  def apply(first: TransferEncoding, more: TransferEncoding*): TE = apply(immutable.Seq(first +: more: _*))
}
final case class TE(acceptableEncodings: immutable.Seq[TransferEncoding]) extends jm.headers.TE with RequestHeader {
  def append(encodings: immutable.Seq[TransferEncoding]) = TE(this.acceptableEncodings ++ encodings)
  def renderValue[R <: Rendering](r: R): r.type = r.~~(acceptableEncodings)(`Transfer-Encoding`.encodingsRenderer)
  protected def companion = TE

  /** Java API */
  def getAcceptableEncodings: Iterable[jm.TransferEncoding] = acceptableEncodings.asJava
}

object `Timeout-Access` extends ModeledCompanion[`Timeout-Access`]
final case class `Timeout-Access`(timeoutAccess: pekko.http.scaladsl.TimeoutAccess)
    extends jm.headers.TimeoutAccess with SyntheticHeader {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ timeoutAccess.toString
  protected def companion = `Timeout-Access`
}

/**
 * Model for the synthetic `Tls-Session-Info` header which carries the SSLSession of the connection
 * the message carrying this header was received with.
 *
 * This header will only be added if it enabled in the configuration by setting
 *
 * ```
 * pekko.http.[client|server].parsing.tls-session-info-header = on
 * ```
 */
object `Tls-Session-Info` extends ModeledCompanion[`Tls-Session-Info`]
final case class `Tls-Session-Info`(session: SSLSession) extends jm.headers.TlsSessionInfo with SyntheticHeader
    with ScalaSessionAPI {
  def renderValue[R <: Rendering](r: R): r.type = r ~~ session.toString
  protected def companion = `Tls-Session-Info`

  /** Java API */
  def getSession: SSLSession = session
}

// https://tools.ietf.org/html/rfc7230#section-3.3.1
object `Transfer-Encoding` extends ModeledCompanion[`Transfer-Encoding`] {
  def apply(first: TransferEncoding, more: TransferEncoding*): `Transfer-Encoding` =
    apply(immutable.Seq(first +: more: _*))
  implicit val encodingsRenderer: Renderer[immutable.Iterable[TransferEncoding]] =
    Renderer.defaultSeqRenderer[TransferEncoding] // cache
}
final case class `Transfer-Encoding`(encodings: immutable.Seq[TransferEncoding]) extends jm.headers.TransferEncoding
    with RequestResponseHeader {
  require(encodings.nonEmpty, "encodings must not be empty")
  import `Transfer-Encoding`.encodingsRenderer
  def isChunked: Boolean = encodings.last == TransferEncodings.chunked
  def withChunked: `Transfer-Encoding` =
    if (isChunked) this else `Transfer-Encoding`(encodings :+ TransferEncodings.chunked)
  def withChunkedPeeled: Option[`Transfer-Encoding`] =
    if (isChunked) {
      encodings.init match {
        case Nil       => None
        case remaining => Some(`Transfer-Encoding`(remaining))
      }
    } else Some(this)
  def append(encodings: immutable.Seq[TransferEncoding]) = `Transfer-Encoding`(this.encodings ++ encodings)
  def renderValue[R <: Rendering](r: R): r.type = r ~~ encodings
  protected def companion = `Transfer-Encoding`

  /** Java API */
  def getEncodings: Iterable[jm.TransferEncoding] = encodings.asJava
}

// https://tools.ietf.org/html/rfc7230#section-6.7
object Upgrade extends ModeledCompanion[Upgrade] {
  implicit val protocolsRenderer: Renderer[immutable.Iterable[UpgradeProtocol]] =
    Renderer.defaultSeqRenderer[UpgradeProtocol]
}
final case class Upgrade(protocols: immutable.Seq[UpgradeProtocol]) extends RequestResponseHeader {
  import Upgrade.protocolsRenderer
  protected[http] def renderValue[R <: Rendering](r: R): r.type = r ~~ protocols

  protected def companion = Upgrade

  def hasWebSocket: Boolean = protocols.exists(_.name.equalsIgnoreCase("websocket"))
}

// https://tools.ietf.org/html/rfc7231#section-5.5.3
object `User-Agent` extends ModeledCompanion[`User-Agent`] {
  def apply(products: String): `User-Agent` = apply(ProductVersion.parseMultiple(products))
  def apply(first: ProductVersion, more: ProductVersion*): `User-Agent` = apply(immutable.Seq(first +: more: _*))
  implicit val productsRenderer: Renderer[immutable.Iterable[ProductVersion]] =
    Renderer.seqRenderer[ProductVersion](separator = " ") // cache
}
final case class `User-Agent`(products: immutable.Seq[ProductVersion]) extends jm.headers.UserAgent with RequestHeader {
  require(products.nonEmpty, "products must not be empty")
  import `User-Agent`.productsRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ products
  protected def companion = `User-Agent`

  /** Java API */
  def getProducts: Iterable[jm.headers.ProductVersion] = products.asJava
}

// https://tools.ietf.org/html/rfc7235#section-4.1
object `WWW-Authenticate` extends ModeledCompanion[`WWW-Authenticate`] {
  def apply(first: HttpChallenge, more: HttpChallenge*): `WWW-Authenticate` = apply(immutable.Seq(first +: more: _*))
  implicit val challengesRenderer: Renderer[immutable.Iterable[HttpChallenge]] =
    Renderer.defaultSeqRenderer[HttpChallenge] // cache
}
final case class `WWW-Authenticate`(challenges: immutable.Seq[HttpChallenge]) extends jm.headers.WWWAuthenticate
    with ResponseHeader {
  require(challenges.nonEmpty, "challenges must not be empty")
  import `WWW-Authenticate`.challengesRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ challenges
  protected def companion = `WWW-Authenticate`

  /** Java API */
  def getChallenges: Iterable[jm.headers.HttpChallenge] = challenges.asJava
}

// https://en.wikipedia.org/wiki/X-Forwarded-For
object `X-Forwarded-For` extends ModeledCompanion[`X-Forwarded-For`] {
  def apply(first: RemoteAddress, more: RemoteAddress*): `X-Forwarded-For` = apply(immutable.Seq(first +: more: _*))
  implicit val addressesRenderer: Renderer[immutable.Iterable[RemoteAddress]] = {
    implicit val singleAddressRenderer = RemoteAddress.renderWithoutPort
    Renderer.defaultSeqRenderer[RemoteAddress] // cache
  }
}
final case class `X-Forwarded-For`(addresses: immutable.Seq[RemoteAddress]) extends jm.headers.XForwardedFor
    with RequestHeader with SensitiveHttpHeader {
  require(addresses.nonEmpty, "addresses must not be empty")
  import `X-Forwarded-For`.addressesRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ addresses
  protected def companion = `X-Forwarded-For`

  /** Java API */
  def getAddresses: Iterable[jm.RemoteAddress] = addresses.asJava
}

object `X-Forwarded-Host` extends ModeledCompanion[`X-Forwarded-Host`] {
  // keep overly accurate return type for bin compat
  implicit val hostRenderer: UriRendering.HostRenderer.type = UriRendering.HostRenderer // cache
}

/**
 * De-facto standard as per https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-Host
 */
@ApiMayChange
final case class `X-Forwarded-Host`(host: Uri.Host) extends jm.headers.XForwardedHost
    with RequestHeader with SensitiveHttpHeader {
  import `X-Forwarded-Host`.hostRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ host
  protected def companion = `X-Forwarded-Host`

  /** Java API */
  def getHost: jm.Host = host.asJava
}

object `X-Forwarded-Proto` extends ModeledCompanion[`X-Forwarded-Proto`]

/**
 * de-facto standard as per https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-Proto
 */
@ApiMayChange
final case class `X-Forwarded-Proto`(protocol: String) extends jm.headers.XForwardedProto
    with RequestHeader {
  require(protocol.nonEmpty, "protocol must not be empty")
  def renderValue[R <: Rendering](r: R): r.type = r ~~ protocol

  protected def companion = `X-Forwarded-Proto`

  /** Java API */
  def getProtocol: String = protocol
}

object `X-Real-Ip` extends ModeledCompanion[`X-Real-Ip`] {
  implicit val addressRenderer: Renderer[RemoteAddress] = RemoteAddress.renderWithoutPort // cache
}
final case class `X-Real-Ip`(address: RemoteAddress) extends jm.headers.XRealIp
    with RequestHeader with SensitiveHttpHeader {
  import `X-Real-Ip`.addressRenderer
  def renderValue[R <: Rendering](r: R): r.type = r ~~ address
  protected def companion = `X-Real-Ip`
}

@ApiMayChange
object Trailer extends ModeledCompanion[Trailer] {
  private implicit val trailersRenderer: Renderer[immutable.Iterable[String]] =
    Renderer.defaultSeqRenderer[String]

  def apply(values: immutable.Seq[String]): Trailer = {
    val clean = values.map(_.trim).filter(_.nonEmpty)
    val (forbidden, allowed) = clean.partition(name => isForbidden(name.toRootLowerCase))
    if (clean.isEmpty)
      throw IllegalHeaderException(
        "Trailer values must not be empty",
        "No valid header names specified")
    else if (forbidden.nonEmpty) {
      val forbiddenInput = forbidden.mkString("[", ", ", "]")
      throw IllegalHeaderException(
        "Trailer values must not contain forbidden header names",
        s"Trailer contained $forbiddenInput")
    } else new Trailer(allowed)
  }

  /**
   * Non-exhaustive set of header names that are disallowed in the `Trailer` header.
   * See <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-4.1.2">RFC 7230, Section 4.1.2</a>
   */
  private val isForbidden: Set[String] =
    Set(
      Trailer,
      // Framing
      `Transfer-Encoding`,
      `Content-Length`,
      // Routing
      Host,
      // Modifiers
      Expect,
      Range,
      `If-Match`,
      `If-None-Match`,
      `If-Modified-Since`,
      `If-Unmodified-Since`,
      `Cache-Control`,
      TE,
      // Auth and cookies
      Authorization,
      `Proxy-Authorization`,
      `Cookie`,
      `Set-Cookie`,
      // Response control
      `WWW-Authenticate`,
      Age,
      // Payload processing
      `Content-Encoding`,
      `Content-Type`,
      `Content-Range`).map(_.lowercaseName)
}

/**
 * The `Trailer` header is used before a message body to indicate which fields will be present
 * in the trailers when using chunked transfer encoding.
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-4.4">RFC 7230, Section 4.4</a>
 *
 * @since 1.3.0
 */
@ApiMayChange
final case class Trailer private (values: immutable.Seq[String]) extends jm.headers.Trailer with RequestResponseHeader {
  require(values.nonEmpty, "Trailer values must not be empty")
  import Trailer.trailersRenderer
  def getTrailers: Iterable[String] = values.asJava
  protected[http] def renderValue[R <: Rendering](r: R): r.type = r ~~ values
  protected def companion: ModeledCompanion[Trailer] = Trailer
}
