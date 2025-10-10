/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.caching

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.caching.javadsl.Cache
import pekko.http.impl.util.JavaMapping

/** INTERNAL API */
@InternalApi
private[pekko] object CacheJavaMapping {

  def cacheMapping[JK, JV, SK <: JK, SV <: JV] =
    new JavaMapping[Cache[JK, JV], pekko.http.caching.scaladsl.Cache[SK, SV]] {
      def toScala(javaObject: Cache[JK, JV]): pekko.http.caching.scaladsl.Cache[SK, SV] =
        javaObject.asInstanceOf[pekko.http.caching.scaladsl.Cache[SK, SV]]

      def toJava(scalaObject: pekko.http.caching.scaladsl.Cache[SK, SV]): Cache[JK, JV] =
        scalaObject.asInstanceOf[Cache[JK, JV]]
    }

  object Implicits {

    implicit object CachingSettings extends JavaMapping.Inherited[javadsl.CachingSettings, scaladsl.CachingSettings]
    implicit object LfuCacheSettings extends JavaMapping.Inherited[javadsl.LfuCacheSettings, scaladsl.LfuCacheSettings]
  }
}
