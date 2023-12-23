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

package org.apache.pekko.http.cors.scaladsl.settings

import java.util.OptionalLong
import java.util.concurrent.TimeUnit
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.DoNotInherit
import pekko.http.cors.javadsl
import pekko.http.cors.scaladsl.model.{ HttpHeaderRange, HttpOriginMatcher }
import pekko.http.cors.javadsl.{ model => jmcorsmodel }
import pekko.http.impl.util.SettingsCompanionImpl
import pekko.http.javadsl.{ model => jmmodel }
import pekko.http.scaladsl.model.headers.HttpOrigin
import pekko.http.scaladsl.model.{ HttpHeader, HttpMethod, HttpMethods }
import pekko.util.OptionConverters._
import com.typesafe.config.ConfigException.{ Missing, WrongType }
import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.util.Try

/**
 * Settings used by the CORS directives.
 *
 * Public API but not intended for subclassing.
 */
@DoNotInherit
abstract class CorsSettings private[pekko] () extends javadsl.settings.CorsSettings { self: CorsSettingsImpl =>

  /**
   * If `true`, allow generic requests (that are outside the scope of the specification) to pass through the directive.
   * Else, strict CORS filtering is applied and any invalid request will be rejected.
   *
   * Default: `true`
   */
  def allowGenericHttpRequests: Boolean

  /**
   * Indicates whether the resource supports user credentials. If `true`, the header `Access-Control-Allow-Credentials`
   * is set in the response, indicating that the actual request can include user credentials. Examples of user
   * credentials are: cookies, HTTP authentication or client-side certificates.
   *
   * Default: `true`
   *
   * @see
   *   [[https://www.w3.org/TR/cors/#access-control-allow-credentials-response-header Access-Control-Allow-Credentials]]
   */
  def allowCredentials: Boolean

  /**
   * List of origins that the CORS filter must allow. Can also be set to `*` to allow access to the resource from any
   * origin. Controls the content of the `Access-Control-Allow-Origin` response header: if parameter is `*` and
   * credentials are not allowed, a `*` is set in `Access-Control-Allow-Origin`. Otherwise, the origins given in the
   * `Origin` request header are echoed.
   *
   * Hostname starting with `*.` will match any sub-domain. The scheme and the port are always strictly matched.
   *
   * The actual or preflight request is rejected if any of the origins from the request is not allowed.
   *
   * Default: `HttpOriginMatcher.*`
   *
   * @see
   *   [[https://www.w3.org/TR/cors/#access-control-allow-origin-response-header Access-Control-Allow-Origin]]
   */
  def allowedOrigins: HttpOriginMatcher

  /**
   * List of request headers that can be used when making an actual request. Controls the content of the
   * `Access-Control-Allow-Headers` header in a preflight response: if parameter is `*`, the headers from
   * `Access-Control-Request-Headers` are echoed. Otherwise the parameter list is returned as part of the header.
   *
   * Default: `HttpHeaderRange.*`
   *
   * @see
   *   [[https://www.w3.org/TR/cors/#access-control-allow-headers-response-header Access-Control-Allow-Headers]]
   */
  def allowedHeaders: HttpHeaderRange

  /**
   * List of methods that can be used when making an actual request. The list is returned as part of the
   * `Access-Control-Allow-Methods` preflight response header.
   *
   * The preflight request will be rejected if the `Access-Control-Request-Method` header's method is not part of the
   * list.
   *
   * Default: `Seq(GET, POST, HEAD, OPTIONS)`
   *
   * @see
   *   [[https://www.w3.org/TR/cors/#access-control-allow-methods-response-header Access-Control-Allow-Methods]]
   */
  def allowedMethods: Seq[HttpMethod]

  /**
   * List of headers (other than simple response headers) that browsers are allowed to access. If not empty, this list
   * is returned as part of the `Access-Control-Expose-Headers` header in the actual response.
   *
   * Default: `Seq.empty`
   *
   * @see
   *   [[https://www.w3.org/TR/cors/#simple-response-header Simple response headers]]
   * @see
   *   [[https://www.w3.org/TR/cors/#access-control-expose-headers-response-header Access-Control-Expose-Headers]]
   */
  def exposedHeaders: Seq[String]

  /**
   * When set, the amount of seconds the browser is allowed to cache the results of a preflight request. This value is
   * returned as part of the `Access-Control-Max-Age` preflight response header. If `None`, the header is not added to
   * the preflight response.
   *
   * Default: `Some(30 * 60)`
   *
   * @see
   *   [[https://www.w3.org/TR/cors/#access-control-max-age-response-header Access-Control-Max-Age]]
   */
  def maxAge: Option[Long]

  /* Java APIs */

  override def getAllowGenericHttpRequests: Boolean = this.allowGenericHttpRequests
  override def getAllowCredentials: Boolean = this.allowCredentials
  override def getAllowedOrigins: jmcorsmodel.HttpOriginMatcher = this.allowedOrigins
  override def getAllowedHeaders: jmcorsmodel.HttpHeaderRange = this.allowedHeaders
  override def getAllowedMethods: java.lang.Iterable[jmmodel.HttpMethod] =
    (this.allowedMethods: Seq[pekko.http.javadsl.model.HttpMethod]).asJava
  override def getExposedHeaders: java.lang.Iterable[String] = this.exposedHeaders.asJava
  override def getMaxAge: OptionalLong = this.maxAge.toJavaPrimitive

  override def withAllowGenericHttpRequests(newValue: Boolean): CorsSettings = {
    copy(allowGenericHttpRequests = newValue)
  }
  override def withAllowCredentials(newValue: Boolean): CorsSettings = {
    copy(allowCredentials = newValue)
  }
  override def withAllowedOrigins(newValue: javadsl.model.HttpOriginMatcher): CorsSettings = {
    copy(allowedOrigins = newValue.asInstanceOf[HttpOriginMatcher])
  }
  override def withAllowedHeaders(newValue: javadsl.model.HttpHeaderRange): CorsSettings = {
    copy(allowedHeaders = newValue.asInstanceOf[HttpHeaderRange])
  }
  override def withAllowedMethods(
      newValue: java.lang.Iterable[pekko.http.javadsl.model.HttpMethod]): CorsSettings = {
    copy(allowedMethods = newValue.asScala.toList.asInstanceOf[List[HttpMethod]])
  }
  override def withExposedHeaders(newValue: java.lang.Iterable[String]): CorsSettings = {
    copy(exposedHeaders = newValue.asScala.toList)
  }
  override def withMaxAge(newValue: OptionalLong): CorsSettings = {
    copy(maxAge = newValue.toScala)
  }

  // overloads for Scala idiomatic use
  def withAllowedOrigins(newValue: HttpOriginMatcher): CorsSettings = copy(allowedOrigins = newValue)
  def withAllowedHeaders(newValue: HttpHeaderRange): CorsSettings = copy(allowedHeaders = newValue)
  def withAllowedMethods(newValue: Seq[HttpMethod]): CorsSettings = copy(allowedMethods = newValue)
  def withExposedHeaders(newValue: Seq[String]): CorsSettings = copy(exposedHeaders = newValue)
  def withMaxAge(newValue: Option[Long]): CorsSettings = copy(maxAge = newValue)

  private[pekko] def preflightResponseHeaders(origins: Seq[HttpOrigin], requestHeaders: Seq[String]): List[HttpHeader]
  private[pekko] def actualResponseHeaders(origins: Seq[HttpOrigin]): List[HttpHeader]
}

object CorsSettings extends SettingsCompanionImpl[CorsSettings]("pekko.http.cors") {

  /**
   * Creates an instance of CorsSettings using the configuration provided by the given ActorSystem.
   */
  implicit def default(implicit system: ActorSystem): CorsSettings = apply(system)

  /**
   * Settings from the default loaded configuration. Note that application code may want to use the `apply()` methods
   * instead to have more control over the source of the configuration.
   */
  @deprecated("Use other CorsSettings constructors", "1.0.0")
  def defaultSettings: CorsSettings = apply(ConfigFactory.load(getClass.getClassLoader))

  def fromSubConfig(root: Config, config: Config): CorsSettings = {
    def parseStringList(path: String): List[String] =
      Try(config.getStringList(path).asScala.toList).recover { case _: WrongType =>
        config.getString(path).split(" ").toList
      }.get

    def parseSeconds(path: String): Option[Long] =
      Try(Some(config.getLong(path))).recover {
        case _: WrongType => Some(config.getDuration(path, TimeUnit.SECONDS))
        case _: Missing   => None
      }.get

    CorsSettingsImpl(
      allowGenericHttpRequests = config.getBoolean("allow-generic-http-requests"),
      allowCredentials = config.getBoolean("allow-credentials"),
      allowedOrigins = parseStringList("allowed-origins") match {
        case List("*") => HttpOriginMatcher.*
        case origins   => HttpOriginMatcher(origins.map(HttpOrigin(_)): _*)
      },
      allowedHeaders = parseStringList("allowed-headers") match {
        case List("*") => HttpHeaderRange.*
        case headers   => HttpHeaderRange(headers: _*)
      },
      allowedMethods = parseStringList("allowed-methods")
        .map(method => HttpMethods.getForKey(method).getOrElse(HttpMethod.custom(method))),
      exposedHeaders = parseStringList("exposed-headers"),
      maxAge = parseSeconds("max-age"))
  }

}
