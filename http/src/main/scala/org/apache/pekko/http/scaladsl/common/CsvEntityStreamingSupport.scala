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
import pekko.stream.scaladsl.{ Flow, Framing }
import pekko.util.ByteString

final class CsvEntityStreamingSupport private[pekko] (
    maxLineLength: Int,
    val supported: ContentTypeRange,
    val contentType: ContentType,
    val framingRenderer: Flow[ByteString, ByteString, NotUsed],
    val parallelism: Int,
    val unordered: Boolean) extends common.CsvEntityStreamingSupport {
  import pekko.http.impl.util.JavaMapping.Implicits._

  def this(maxObjectSize: Int) =
    this(
      maxObjectSize,
      ContentTypeRange(ContentTypes.`text/csv(UTF-8)`),
      ContentTypes.`text/csv(UTF-8)`,
      { val newline = ByteString("\n"); Flow[ByteString].map(bs => bs ++ newline) },
      1, false)

  override val framingDecoder: Flow[ByteString, ByteString, NotUsed] =
    Framing.delimiter(ByteString("\n"), maxLineLength)

  override def withFramingRendererFlow(
      framingRendererFlow: pekko.stream.javadsl.Flow[ByteString, ByteString, NotUsed]): CsvEntityStreamingSupport =
    withFramingRenderer(framingRendererFlow.asScala)
  def withFramingRenderer(framingRendererFlow: Flow[ByteString, ByteString, NotUsed]): CsvEntityStreamingSupport =
    new CsvEntityStreamingSupport(maxLineLength, supported, contentType, framingRendererFlow, parallelism, unordered)

  override def withContentType(ct: jm.ContentType): CsvEntityStreamingSupport =
    new CsvEntityStreamingSupport(maxLineLength, supported, ct.asScala, framingRenderer, parallelism, unordered)
  override def withSupported(range: jm.ContentTypeRange): CsvEntityStreamingSupport =
    new CsvEntityStreamingSupport(maxLineLength, range.asScala, contentType, framingRenderer, parallelism, unordered)
  override def withParallelMarshalling(parallelism: Int, unordered: Boolean): CsvEntityStreamingSupport =
    new CsvEntityStreamingSupport(maxLineLength, supported, contentType, framingRenderer, parallelism, unordered)

  override def toString = s"""${Logging.simpleName(getClass)}($maxLineLength, $supported, $contentType)"""
}
