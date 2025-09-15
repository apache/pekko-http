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

package org.apache.pekko.http.javadsl.server.directives

import java.util.{ List => JList, Map => JMap }
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.Optional
import java.util.function.{ Function => JFunction }

import org.apache.pekko
import pekko.http.javadsl.unmarshalling.Unmarshaller
import pekko.http.javadsl.server.Route
import pekko.http.scaladsl.server.{ Directives => D }
import pekko.http.scaladsl.server.directives.ParameterDirectives._

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

abstract class FormFieldDirectives extends FileUploadDirectives {

  def formField(name: String, inner: JFunction[String, Route]): Route = RouteAdapter(
    D.formField(name) { value =>
      inner.apply(value).delegate
    })

  @CorrespondsTo("formField")
  def formFieldOptional(name: String, inner: JFunction[Optional[String], Route]): Route = RouteAdapter(
    D.formField(name.optional) { value =>
      inner.apply(value.asJava).delegate
    })

  @CorrespondsTo("formFieldSeq")
  def formFieldList(name: String, inner: JFunction[java.util.List[String], Route]): Route = RouteAdapter(
    D.formField(name.repeated) { values =>
      inner.apply(values.toSeq.asJava).delegate
    })

  def formField[T](t: Unmarshaller[String, T], name: String, inner: JFunction[T, Route]): Route = {
    import t.asScala
    RouteAdapter(
      D.formField(name.as[T]) { value =>
        inner.apply(value).delegate
      })
  }

  @CorrespondsTo("formField")
  def formFieldOptional[T](t: Unmarshaller[String, T], name: String, inner: JFunction[Optional[T], Route]): Route = {
    import t.asScala
    RouteAdapter(
      D.formField(name.as[T].optional) { value =>
        inner.apply(value.toJava).delegate
      })
  }

  @CorrespondsTo("formFieldSeq")
  def formFieldList[T](t: Unmarshaller[String, T], name: String, inner: JFunction[java.util.List[T], Route]): Route = {
    import t.asScala
    RouteAdapter(
      D.formField(name.as[T].optional) { values =>
        inner.apply(values.toSeq.asJava).delegate
      })
  }

  /**
   * Extracts HTTP form fields from the request as a ``Map<String, String>``.
   */
  def formFieldMap(inner: JFunction[JMap[String, String], Route]): Route = RouteAdapter {
    D.formFieldMap { map => inner.apply(map.asJava).delegate }
  }

  /**
   * Extracts HTTP form fields from the request as a ``Map<String, List<String>>``.
   */
  def formFieldMultiMap(inner: JFunction[JMap[String, JList[String]], Route]): Route = RouteAdapter {
    D.formFieldMultiMap { map => inner.apply(map.mapValues { l => l.asJava }.toMap.asJava).delegate }
  }

  /**
   * Extracts HTTP form fields from the request as a ``Map.Entry<String, String>>``.
   */
  @CorrespondsTo("formFieldSeq")
  def formFieldList(inner: JFunction[JList[JMap.Entry[String, String]], Route]): Route = RouteAdapter {
    D.formFieldSeq { list =>
      val entries: Seq[JMap.Entry[String, String]] = list.map { e => new SimpleImmutableEntry(e._1, e._2) }
      inner.apply(entries.asJava).delegate
    }
  }

}
