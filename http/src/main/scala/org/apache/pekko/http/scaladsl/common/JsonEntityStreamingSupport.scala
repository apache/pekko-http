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

package org.apache.pekko.http.scaladsl.common

import org.apache.pekko
import pekko.NotUsed
import pekko.event.Logging
import pekko.http.javadsl.{ common, model => jm }
import pekko.http.scaladsl.model.{ ContentType, ContentTypeRange, ContentTypes }
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

final class JsonEntityStreamingSupport private[pekko] (
    maxObjectSize: Int,
    val supported: ContentTypeRange,
    val contentType: ContentType,
    val framingRenderer: Flow[ByteString, ByteString, NotUsed],
    val parallelism: Int,
    val unordered: Boolean) extends common.JsonEntityStreamingSupport {
  import pekko.http.impl.util.JavaMapping.Implicits._

  def this(maxObjectSize: Int) =
    this(
      maxObjectSize,
      ContentTypeRange(ContentTypes.`application/json`),
      ContentTypes.`application/json`,
      Flow[ByteString].intersperse(ByteString("["), ByteString(","), ByteString("]")),
      1, false)

  override val framingDecoder: Flow[ByteString, ByteString, NotUsed] =
    pekko.stream.scaladsl.JsonFraming.objectScanner(maxObjectSize)

  override def withFramingRendererFlow(
      framingRendererFlow: pekko.stream.javadsl.Flow[ByteString, ByteString, NotUsed]): JsonEntityStreamingSupport =
    withFramingRenderer(framingRendererFlow.asScala)
  def withFramingRenderer(framingRendererFlow: Flow[ByteString, ByteString, NotUsed]): JsonEntityStreamingSupport =
    new JsonEntityStreamingSupport(maxObjectSize, supported, contentType, framingRendererFlow, parallelism, unordered)

  override def withContentType(ct: jm.ContentType): JsonEntityStreamingSupport =
    new JsonEntityStreamingSupport(maxObjectSize, supported, ct.asScala, framingRenderer, parallelism, unordered)
  override def withSupported(range: jm.ContentTypeRange): JsonEntityStreamingSupport =
    new JsonEntityStreamingSupport(maxObjectSize, range.asScala, contentType, framingRenderer, parallelism, unordered)
  override def withParallelMarshalling(parallelism: Int, unordered: Boolean): JsonEntityStreamingSupport =
    new JsonEntityStreamingSupport(maxObjectSize, supported, contentType, framingRenderer, parallelism, unordered)

  override def toString = s"""${Logging.simpleName(getClass)}($maxObjectSize, $supported, $contentType)"""

}
