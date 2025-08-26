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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.http.impl.model.parser.CharacterClasses
import pekko.http.impl.util.StringRendering
import pekko.http.scaladsl.model.MediaTypes._

/**
 * Simple model for `application/x-www-form-urlencoded` form data.
 */
final case class FormData(fields: Uri.Query) {
  def toEntity: pekko.http.scaladsl.model.RequestEntity =
    toEntityWithCharset(`application/x-www-form-urlencoded`.charset)

  private def toEntityWithCharset(charset: HttpCharset): pekko.http.scaladsl.model.RequestEntity = {
    val render: StringRendering =
      UriRendering.renderQuery(new StringRendering, this.fields, charset.nioCharset, CharacterClasses.unreserved)
    HttpEntity(`application/x-www-form-urlencoded`, render.get)
  }
}

object FormData {
  val Empty = FormData(Uri.Query.Empty)

  def apply(fields: Map[String, String]): FormData =
    if (fields.isEmpty) Empty else FormData(Uri.Query(fields))

  def apply(fields: (String, String)*): FormData =
    if (fields.isEmpty) Empty else FormData(Uri.Query(fields: _*))
}
