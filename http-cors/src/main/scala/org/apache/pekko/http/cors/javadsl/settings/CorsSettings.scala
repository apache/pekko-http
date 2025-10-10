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

package org.apache.pekko.http.cors.javadsl.settings

import java.util.OptionalLong

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.DoNotInherit
import pekko.http.cors.javadsl.model.{ HttpHeaderRange, HttpOriginMatcher }
import pekko.http.cors.scaladsl
import pekko.http.cors.scaladsl.settings.CorsSettingsImpl
import pekko.http.javadsl.model.HttpMethod

import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class CorsSettings { self: CorsSettingsImpl =>

  def getAllowGenericHttpRequests: Boolean
  def getAllowCredentials: Boolean
  def getAllowedOrigins: HttpOriginMatcher
  def getAllowedHeaders: HttpHeaderRange
  def getAllowedMethods: java.lang.Iterable[HttpMethod]
  def getExposedHeaders: java.lang.Iterable[String]
  def getMaxAge: OptionalLong

  def withAllowGenericHttpRequests(newValue: Boolean): CorsSettings
  def withAllowCredentials(newValue: Boolean): CorsSettings
  def withAllowedOrigins(newValue: HttpOriginMatcher): CorsSettings
  def withAllowedHeaders(newValue: HttpHeaderRange): CorsSettings
  def withAllowedMethods(newValue: java.lang.Iterable[HttpMethod]): CorsSettings
  def withExposedHeaders(newValue: java.lang.Iterable[String]): CorsSettings
  def withMaxAge(newValue: OptionalLong): CorsSettings
}

object CorsSettings {

  /**
   * Creates an instance of settings using the given Config.
   */
  def create(config: Config): CorsSettings = scaladsl.settings.CorsSettings(config)

  /**
   * Creates an instance of settings using the given String of config overrides to override settings set in the class
   * loader of this class (i.e. by application.conf or reference.conf files in the class loader of this class).
   */
  def create(configOverrides: String): CorsSettings = scaladsl.settings.CorsSettings(configOverrides)

  /**
   * Creates an instance of CorsSettings using the configuration provided by the given ActorSystem.
   */
  def create(system: ActorSystem): CorsSettings = scaladsl.settings.CorsSettings(system)
}
