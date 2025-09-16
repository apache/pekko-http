/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.testkit

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.http.javadsl.model.ws.Message
import pekko.stream.Materializer
import pekko.stream.javadsl.Flow
import pekko.stream.scaladsl
import pekko.util.ByteString
import pekko.util.JavaDurationConverters._

import pekko.http.scaladsl.{ testkit => st }

import pekko.http.impl.util.JavaMapping.Implicits._

import scala.concurrent.duration._

/**
 * A WSProbe is a probe that implements a `Flow[Message, Message, Unit]` for testing
 * websocket code.
 *
 * Requesting elements is handled automatically.
 */
class WSProbe(delegate: st.WSProbe) {

  def flow: Flow[Message, Message, NotUsed] = {
    val underlying = scaladsl.Flow[Message].map(_.asScala).via(delegate.flow).map(_.asJava)
    new Flow[Message, Message, NotUsed](underlying)
  }

  /**
   * Send the given messages out of the flow.
   */
  def sendMessage(message: Message): Unit = delegate.sendMessage(message.asScala)

  /**
   * Send a text message containing the given string out of the flow.
   */
  def sendMessage(text: String): Unit = delegate.sendMessage(text)

  /**
   * Send a binary message containing the given bytes out of the flow.
   */
  def sendMessage(bytes: ByteString): Unit = delegate.sendMessage(bytes)

  /**
   * Complete the output side of the flow.
   */
  def sendCompletion(): Unit = delegate.sendCompletion()

  /**
   * Expect a message on the input side of the flow.
   */
  def expectMessage(): Message = delegate.expectMessage()

  /**
   * Expect a text message on the input side of the flow and compares its payload with the given one.
   * If the received message is streamed its contents are collected and then asserted against the given
   * String.
   */
  def expectMessage(text: String): Unit = delegate.expectMessage(text)

  /**
   * Expect a binary message on the input side of the flow and compares its payload with the given one.
   * If the received message is streamed its contents are collected and then asserted against the given
   * ByteString.
   */
  def expectMessage(bytes: ByteString): Unit = delegate.expectMessage(bytes)

  /**
   * Expect no message on the input side of the flow.
   */
  def expectNoMessage(): Unit = delegate.expectNoMessage()

  /**
   * Expect no message on the input side of the flow for the given maximum duration.
   */
  def expectNoMessage(max: FiniteDuration): Unit = delegate.expectNoMessage(max)

  /**
   * Expect no message on the input side of the flow for the given maximum duration.
   * @since 1.3.0
   */
  def expectNoMessage(max: java.time.Duration): Unit = delegate.expectNoMessage(max.asScala)

  /**
   * Expect completion on the input side of the flow.
   */
  def expectCompletion(): Unit = delegate.expectCompletion()

}

object WSProbe {

  // A convenient method to create WSProbe with default maxChunks and maxChunkCollectionMills
  def create(system: ActorSystem, materializer: Materializer): WSProbe = {
    create(system, materializer, 1000, 5000)
  }

  /**
   * Creates a WSProbe to use in tests against websocket handlers.
   *
   * @param maxChunks The maximum number of chunks to collect for streamed messages.
   * @param maxChunkCollectionMills The maximum time in milliseconds to collect chunks for streamed messages.
   */
  def create(
      system: ActorSystem, materializer: Materializer, maxChunks: Int, maxChunkCollectionMills: Long): WSProbe = {
    val delegate = st.WSProbe(maxChunks, maxChunkCollectionMills)(system, materializer)
    new WSProbe(delegate)
  }

}
