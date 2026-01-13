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

package org.apache.pekko.http.scaladsl.model.ws

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.stream.{ javadsl, Materializer }
import pekko.stream.scaladsl.Source
import pekko.util.{ ByteString, ByteStringBuilder }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.FutureConverters._

//#message-model
/**
 * The ADT for WebSocket messages. A message can either be a binary or a text message.
 */
sealed trait Message extends pekko.http.javadsl.model.ws.Message

/**
 * Represents a WebSocket text message. A text message can either be a [[TextMessage.Strict]] in which case
 * the complete data is already available or it can be [[TextMessage.Streamed]] in which case `textStream`
 * will return a Source streaming the data as it comes in.
 */
sealed trait TextMessage extends pekko.http.javadsl.model.ws.TextMessage with Message {

  /**
   * The contents of this message as a stream.
   */
  def textStream: Source[String, _]

  /**
   * Collects all possible parts and returns a potentially future Strict Message for easier processing.
   * The Future is failed with an TimeoutException if the stream isn't completed after the given timeout.
   */
  def toStrict(timeout: FiniteDuration)(implicit fm: Materializer): Future[TextMessage.Strict] =
    this match {
      case TextMessage.Strict(text)         => Future.successful(TextMessage.Strict(text))
      case TextMessage.Streamed(textStream) => textStream
          .completionTimeout(timeout)
          .runFold(new StringBuilder())((b, s) => b.append(s))
          .map(b => b.toString)(fm.executionContext)
          .map(text => TextMessage.Strict(text))(fm.executionContext)
    }

  /** Java API */
  override def getStreamedText: javadsl.Source[String, _] = textStream.asJava
  override def asScala: TextMessage = this
  override def toStrict(timeoutMillis: Long, materializer: Materializer): CompletionStage[TextMessage.Strict] =
    toStrict(timeoutMillis.millis)(materializer).asJava
}
//#message-model
object TextMessage {
  def apply(text: String): Strict = Strict(text)
  def apply(textStream: Source[String, Any]): TextMessage =
    Streamed(textStream)

  /**
   * A strict [[TextMessage]] that contains the complete data as a [[String]].
   */
  final case class Strict(text: String) extends TextMessage {
    def textStream: Source[String, _] = Source.single(text)
    override def toString: String = s"TextMessage.Strict($text)"

    /** Java API */
    override def getStrictText: String = text
    override def isStrict: Boolean = true
  }

  final case class Streamed(textStream: Source[String, _]) extends TextMessage {
    override def toString: String = s"TextMessage.Streamed($textStream)"

    /** Java API */
    override def getStrictText: String = throw new IllegalStateException("Cannot get strict text for streamed message.")
    override def isStrict: Boolean = false
  }
}

/**
 * Represents a WebSocket binary message. A binary message can either be [[BinaryMessage.Strict]] in which case
 * the complete data is already available or it can be [[BinaryMessage.Streamed]] in which case `dataStream`
 * will return a Source streaming the data as it comes in.
 */
//#message-model
sealed trait BinaryMessage extends pekko.http.javadsl.model.ws.BinaryMessage with Message {

  /**
   * The contents of this message as a stream.
   */
  def dataStream: Source[ByteString, _]

  /**
   * Collects all possible parts and returns a potentially future Strict Message for easier processing.
   * The Future is failed with an TimeoutException if the stream isn't completed after the given timeout.
   */
  def toStrict(timeout: FiniteDuration)(implicit fm: Materializer): Future[BinaryMessage.Strict] =
    this match {
      case BinaryMessage.Strict(binary)         => Future.successful(BinaryMessage.Strict(binary))
      case BinaryMessage.Streamed(binaryStream) => binaryStream
          .completionTimeout(timeout)
          .runFold(new ByteStringBuilder())((b, e) => b.append(e))
          .map(b => b.result())(fm.executionContext)
          .map(binary => BinaryMessage.Strict(binary))(fm.executionContext)
    }

  /** Java API */
  override def getStreamedData: javadsl.Source[ByteString, _] = dataStream.asJava
  override def asScala: BinaryMessage = this
  override def toStrict(timeoutMillis: Long, materializer: Materializer): CompletionStage[BinaryMessage.Strict] =
    toStrict(timeoutMillis.millis)(materializer).asJava
}
//#message-model
object BinaryMessage {
  def apply(data: ByteString): Strict = Strict(data)
  def apply(dataStream: Source[ByteString, Any]): BinaryMessage =
    Streamed(dataStream)

  /**
   * A strict [[BinaryMessage]] that contains the complete data as a [[pekko.util.ByteString]].
   */
  final case class Strict(data: ByteString) extends BinaryMessage {
    def dataStream: Source[ByteString, _] = Source.single(data)
    override def toString: String = s"BinaryMessage.Strict($data)"

    /** Java API */
    override def getStrictData: ByteString = data
    override def isStrict: Boolean = true
  }
  final case class Streamed(dataStream: Source[ByteString, _]) extends BinaryMessage {
    override def toString: String = s"BinaryMessage.Streamed($dataStream)"

    /** Java API */
    override def getStrictData: ByteString =
      throw new IllegalStateException("Cannot get strict data for streamed message.")
    override def isStrict: Boolean = false
  }
}
