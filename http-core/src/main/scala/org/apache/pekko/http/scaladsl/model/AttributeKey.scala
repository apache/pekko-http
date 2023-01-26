/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko.http.javadsl.{ model => jm }

import scala.reflect.ClassTag

case class AttributeKey[T](name: String, private val clazz: Class[_]) extends jm.AttributeKey[T]

object AttributeKey {
  def apply[T: ClassTag](name: String): AttributeKey[T] =
    new AttributeKey[T](name, implicitly[ClassTag[T]].runtimeClass)
}
