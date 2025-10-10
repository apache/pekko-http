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

package org.apache.pekko.http.impl.model

import java.util.Optional
import java.{ util => ju }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.model.parser.CharacterClasses
import pekko.http.impl.util.StringRendering
import pekko.http.javadsl.model.HttpCharset
import pekko.http.javadsl.{ model => jm }
import pekko.http.scaladsl.model.UriRendering
import pekko.http.scaladsl.{ model => sm }
import pekko.japi.Pair
import org.parboiled2.CharPredicate

import scala.jdk.CollectionConverters._
import pekko.http.impl.util.JavaMapping.Implicits._

/** INTERNAL API */
@InternalApi
final private[http] case class JavaQuery(query: sm.Uri.Query) extends jm.Query {
  override def get(key: String): Optional[String] = query.get(key).asJava
  override def toMap: ju.Map[String, String] = query.toMap.asJava
  override def toList: ju.List[Pair[String, String]] = query.map(_.asJava).asJava
  override def getOrElse(key: String, _default: String): String = query.getOrElse(key, _default)
  override def toMultiMap: ju.Map[String, ju.List[String]] = query.toMultiMap.map { case (k, v) =>
    (k, v.asJava)
  }.asJava
  override def getAll(key: String): ju.List[String] = query.getAll(key).asJava
  override def toString = query.toString
  override def withParam(key: String, value: String): jm.Query =
    jm.Query.create(query.map(_.asJava) :+ Pair(key, value): _*)
  override def render(charset: HttpCharset): String =
    UriRendering.renderQuery(new StringRendering, query, charset.nioCharset, CharacterClasses.unreserved).get
  override def render(charset: HttpCharset, keep: CharPredicate): String =
    UriRendering.renderQuery(new StringRendering, query, charset.nioCharset, keep).get
}
