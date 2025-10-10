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

package org.apache.pekko.http.impl.util

import java.io.File
import java.nio.file.Path

import JavaMapping.Implicits._
import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.javadsl.model._
import pekko.http.scaladsl.model

/**
 *  INTERNAL API
 *
 *  Accessors for constructors with default arguments to be used from the Java implementation
 */
@InternalApi
private[http] object JavaAccessors {

  /** INTERNAL API */
  def HttpRequest(): HttpRequest = model.HttpRequest()

  /** INTERNAL API */
  def HttpRequest(uri: String): HttpRequest = model.HttpRequest(uri = uri)

  /** INTERNAL API */
  def HttpResponse(): HttpResponse = model.HttpResponse()

  /** INTERNAL API */
  def HttpEntity(contentType: ContentType, file: File): UniversalEntity =
    model.HttpEntity.fromPath(contentType.asScala, file.toPath)

  /** INTERNAL API */
  def HttpEntity(contentType: ContentType, file: Path): UniversalEntity =
    model.HttpEntity.fromPath(contentType.asScala, file)
}
