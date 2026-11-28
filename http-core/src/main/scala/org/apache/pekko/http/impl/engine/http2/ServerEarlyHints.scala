/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.engine.http2

import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

import org.apache.pekko.Done
import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.http.impl.engine.http2.FrameEvent.ParsedHeadersFrame
import org.apache.pekko.http.scaladsl.model.{ AttributeKey, HttpHeader }
import org.apache.pekko.http.scaladsl.model.EarlyHintsSender
import org.apache.pekko.stream.stage.AsyncCallback

/** Owns the interim response capabilities of one enabled HTTP/2 server connection. */
private[http2] final class ServerEarlyHints(
    mkCallback: (ServerEarlyHints.Offer => Unit) => AsyncCallback[ServerEarlyHints.Offer],
    multiplexer: () => Http2Multiplexer,
    log: LoggingAdapter) {
  import ServerEarlyHints._

  private val senders = mutable.Map.empty[Int, Sender]
  private val buffered = mutable.Map.empty[Int, (Offer, ParsedHeadersFrame)]
  private val submit = new Ready(mkCallback(onOffer))

  def attributesFor(
      streamId: Int, attributes: Map[AttributeKey[?], ?]): Map[AttributeKey[?], ?] = {
    val sender = new Sender(streamId, submit)
    senders(streamId) = sender
    attributes.updated(EarlyHintsSender.key, new EarlyHintsSender(sender.send))
  }

  private def onOffer(offer: Offer): Unit = {
    val streamId = offer.sender.streamId
    if (senders.get(streamId).contains(offer.sender)) {
      val fields = Try {
        // These are application hints, not entity headers: do not derive or inject automatic fields.
        offer.headers.iterator.collect {
          case h
              if h.renderInResponses && !h.is("content-length") && !h.name.startsWith(":") &&
              HttpMessageRendering.allowsHeader(h, isServer = true, isTrailer = false, log) =>
            h.lowercaseName -> h.value
        }.toVector
      }
      fields match {
        case Success(pairs) =>
          val frame = ParsedHeadersFrame(streamId, endStream = false, (":status" -> "103") +: pairs, None, None)
          buffered(streamId) = (offer, frame)
          if (!multiplexer().tryPushEarlyHints(frame)) {
            buffered.remove(streamId)
            offer.sender.reject(offer, new IllegalStateException("The HTTP/2 output cannot buffer Early Hints"))
          }
        case Failure(cause) => offer.sender.reject(offer, cause)
      }
    }
  }

  def onFrameOutput(frame: FrameEvent): Unit = frame match {
    case headers: ParsedHeadersFrame =>
      buffered.get(headers.streamId) match {
        case Some((offer, pendingFrame)) if pendingFrame eq headers =>
          buffered.remove(headers.streamId)
          offer.sender.complete(offer)
        case _ =>
      }
    case _ =>
  }

  def close(streamId: Int): Unit = {
    senders.remove(streamId).foreach(_.close())
    buffered.remove(streamId).foreach { case (_, frame) => multiplexer().discardEarlyHints(frame) }
  }

  def shutdown(): Unit = {
    senders.valuesIterator.foreach(_.close())
    senders.clear()
    buffered.clear()
  }
}

private[http2] object ServerEarlyHints {
  private[http2] sealed trait State
  private final class Ready(val callback: AsyncCallback[Offer]) extends State
  private case object Closed extends State

  // A sender held by application code after closing no longer retains the connection callback.
  private final class Sender(val streamId: Int, ready: Ready) {
    private val state = new AtomicReference[State](ready)

    def send(headers: Seq[HttpHeader]): Future[Done] = state.get() match {
      case available: Ready =>
        val offer = new Offer(this, available, headers)
        if (state.compareAndSet(available, offer)) {
          available.callback.invokeWithFeedback(offer).failed.foreach(cause => reject(offer, cause))(
            ExecutionContext.parasitic)
          offer.result.future
        } else Future.failed(new IllegalStateException("The HTTP/2 Early Hints sender is no longer available"))
      case Closed   => Future.failed(closed())
      case _: Offer => Future.failed(new IllegalStateException("An HTTP/2 Early Hints offer is already pending"))
    }

    def complete(offer: Offer): Unit =
      if (state.compareAndSet(offer, offer.previous)) offer.result.tryComplete(Success(Done))

    def reject(offer: Offer, cause: Throwable): Unit =
      if (state.compareAndSet(offer, offer.previous)) offer.result.tryComplete(Failure(cause))

    def close(): Unit = state.getAndSet(Closed) match {
      case offer: Offer => offer.result.tryFailure(closed())
      case _            =>
    }
  }

  private def closed(): IllegalStateException =
    new IllegalStateException("The HTTP/2 Early Hints sender is closed")

  // Visible to the graph stage solely as the argument to the asynchronous callback factory.
  final class Offer private[ServerEarlyHints] (
      private[ServerEarlyHints] val sender: Sender,
      private[ServerEarlyHints] val previous: Ready,
      private[ServerEarlyHints] val headers: Seq[HttpHeader]) extends State {
    private[ServerEarlyHints] val result = Promise[Done]()
  }
}
