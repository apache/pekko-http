/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.server.directives

import java.util.{ function => jf, Optional }

import org.apache.pekko
import pekko.http.javadsl.model.AttributeKey
import pekko.http.javadsl.server.Route
import pekko.http.scaladsl.server.directives.{ AttributeDirectives => D }

import scala.compat.java8.OptionConverters._

abstract class AttributeDirectives extends HeaderDirectives {
  import pekko.http.impl.util.JavaMapping._

  /**
   * Extracts the value of the request attribute with the given key.
   * If no attribute is found the request is rejected with a [[pekko.http.javadsl.server.MissingAttributeRejection]].
   */
  def attribute[T](key: AttributeKey[T], inner: jf.Function[T, Route]) = RouteAdapter {
    D.attribute(toScala(key)) { value: T =>
      inner.apply(value).delegate
    }
  }

  /**
   * Extracts the value of the optional request attribute with the given key.
   */
  def optionalAttribute[T](key: AttributeKey[T], inner: jf.Function[Optional[T], Route]) = RouteAdapter {
    D.optionalAttribute(toScala(key)) { value =>
      inner.apply(value.asJava).delegate
    }
  }

}
