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

package org.apache.pekko.http.impl.engine.ws

import java.nio.CharBuffer
import java.nio.charset.{ CharacterCodingException, StandardCharsets }

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.ws.FrameHandler.{ UserHandlerErredOut, _ }
import pekko.http.scaladsl.model.AttributeKey
import pekko.stream.scaladsl.Flow
import pekko.stream.stage._
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }

/**
 * Implements the transport connection close handling at the end of the pipeline.
 *
 * INTERNAL API
 */
@InternalApi
private[http] class FrameOutHandler(serverSide: Boolean, _closeTimeout: FiniteDuration, log: LoggingAdapter)
    extends GraphStage[FlowShape[FrameOutHandler.Input, FrameStart]] {
  val in = Inlet[FrameOutHandler.Input]("FrameOutHandler.in")
  val out = Outlet[FrameStart]("FrameOutHandler.out")

  override def shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {

    private val closeTimer = "close-timeout"

    private def startCloseTimer(): Unit =
      if (!isTimerActive(closeTimer)) scheduleOnce(closeTimer, _closeTimeout)

    override protected def onTimer(timerKey: Any): Unit =
      if (timerKey == closeTimer) getHandler(in) match {
        case _: WaitingForPeerCloseFrame =>
          log.debug("Peer did not acknowledge CLOSE frame after {}, closing underlying connection now.", _closeTimeout)
          completeStage()
        case _: WaitingForTransportClose =>
          log.debug(
            "Peer did not close TCP connection after sending CLOSE frame after {}, closing underlying connection now.",
            _closeTimeout)
          completeStage()
        case _ =>
      }

    private def absorbTermination() =
      if (isAvailable(out)) getHandler(out).onPull()

    private object Idle extends InHandler with ProcotolExceptionHandling {
      override def onPush() =
        grab(in) match {
          case start: FrameStart                                                     => push(out, start)
          case DirectAnswer(frame)                                                   => push(out, frame)
          case PeerClosed(code, reason) if !code.exists(Protocol.CloseCodes.isError) =>
            // let user complete it, FIXME: maybe make configurable? immediately, or timeout
            setHandler(in,
              new WaitingForUserHandlerClosed(FrameEvent.closeFrame(code.getOrElse(Protocol.CloseCodes.Regular),
                reason)))
            pull(in)
          case PeerClosed(code, reason) =>
            val closeFrame = FrameEvent.closeFrame(code.getOrElse(Protocol.CloseCodes.Regular), reason)
            if (serverSide) {
              push(out, closeFrame)
              completeStage()
            } else {
              setHandler(in, new WaitingForTransportClose)
              push(out, closeFrame)
            }
          case ActivelyCloseWithCode(code, reason) =>
            val closeFrame = FrameEvent.closeFrame(code.getOrElse(Protocol.CloseCodes.Regular), reason)
            setHandler(in, new WaitingForPeerCloseFrame)
            push(out, closeFrame)
          case UserHandlerCompleted =>
            setHandler(in, new WaitingForPeerCloseFrame)
            val frame = if (serverSide) inheritedAttributes.get[FrameOutHandler.CompletionClose].map(_.frame) else None
            push(out, frame.getOrElse(FrameEvent.closeFrame(Protocol.CloseCodes.Regular)))
          case UserHandlerErredOut(e) =>
            log.error(e, "Websocket handler failed with {}", e.getMessage)
            setHandler(in, new WaitingForPeerCloseFrame)
            push(out, FrameEvent.closeFrame(Protocol.CloseCodes.UnexpectedCondition, "internal error"))
        }

      override def onUpstreamFinish(): Unit = {
        becomeSendOutCloseFrameAndComplete(FrameEvent.closeFrame(Protocol.CloseCodes.Regular))
        absorbTermination()
      }
    }

    /**
     * peer has closed, we want to wait for user handler to close as well
     */
    private class WaitingForUserHandlerClosed(closeFrame: FrameStart) extends InHandler {
      def onPush() =
        grab(in) match {
          case UserHandlerCompleted   => sendOutLastFrame()
          case UserHandlerErredOut(e) =>
            log.error(e, "Websocket handler failed while waiting for handler completion with {}", e.getMessage)
            sendOutLastFrame()
          case start: FrameStart => push(out, start)
          case _                 => pull(in) // ignore
        }

      private def sendOutLastFrame(): Unit =
        if (serverSide) {
          push(out, closeFrame)
          completeStage()
        } else {
          setHandler(in, new WaitingForTransportClose)
          push(out, closeFrame)
        }

      override def onUpstreamFinish(): Unit =
        fail(out, new IllegalStateException("Mustn't complete before user has completed"))
    }

    /**
     * we have sent out close frame and wait for peer to sent its close frame
     */
    private class WaitingForPeerCloseFrame extends InHandler with ProcotolExceptionHandling {
      startCloseTimer()

      override def onPush() =
        grab(in) match {
          case PeerClosed(code, reason) =>
            if (serverSide) completeStage()
            else {
              // Keep the original close budget while waiting for the server to close the transport.
              setHandler(in, new WaitingForTransportClose)
              pull(in)
            }
          case _ => pull(in) // ignore
        }
    }

    /**
     * Both sides have sent their close frames, server should close the connection first
     */
    private class WaitingForTransportClose extends InHandler with ProcotolExceptionHandling {
      startCloseTimer()

      override def onPush(): Unit = {
        grab(in)
        pull(in)
      }
    }

    /** If upstream has already failed we just wait to be able to deliver our close frame and complete */
    private class SendOutCloseFrameAndComplete(closeFrame: FrameStart) extends InHandler with OutHandler
        with ProcotolExceptionHandling {
      override def onPush() =
        fail(out, new IllegalStateException("Didn't expect push after completion"))

      override def onPull(): Unit = {
        push(out, closeFrame)
        completeStage()
      }

      override def onUpstreamFinish(): Unit =
        absorbTermination()
    }

    def becomeSendOutCloseFrameAndComplete(frameStart: FrameStart): Unit = {
      cancelTimer(closeTimer)
      val inNOutHandler = new SendOutCloseFrameAndComplete(frameStart)
      setHandler(in, inNOutHandler)
      setHandler(out, inNOutHandler)
    }

    /** We handle [[ProtocolException]] in a special way (by terminating with a ProtocolError) */
    private trait ProcotolExceptionHandling extends InHandler {
      @tailrec override final def onUpstreamFailure(cause: Throwable): Unit =
        cause match {
          case p: ProtocolException =>
            becomeSendOutCloseFrameAndComplete(FrameEvent.closeFrame(Protocol.CloseCodes.ProtocolError))
            absorbTermination()
          case x if x.getCause ne null => onUpstreamFailure(x.getCause)
          case _                       =>
            super.onUpstreamFailure(cause)
        }
    }

    // init handlers

    setHandler(in, Idle)
    setHandler(out,
      new OutHandler {
        override def onPull(): Unit = pull(in)
      })

  }
}

private[http] object FrameOutHandler {
  type Input = AnyRef

  final class CompletionClose(val frame: FrameStart) extends Attributes.Attribute
  val completionCloseKey = AttributeKey[CompletionClose]("pekko.http.websocket.completion-close")

  def completionClose(code: Int, reason: String): CompletionClose = {
    import Protocol.CloseCodes
    require(CloseCodes.isValid(code) && code != CloseCodes.ClientRejectsExtension,
      s"Invalid server WebSocket close status: $code")
    require(reason != null && reason.length <= 123, "WebSocket Close reason must be at most 123 UTF-8 bytes")
    val encodedLength =
      try StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(reason)).remaining()
      catch {
        case _: CharacterCodingException =>
          throw new IllegalArgumentException("WebSocket Close reason must be valid UTF-8")
      }
    require(encodedLength <= 123, "WebSocket Close reason must be at most 123 UTF-8 bytes")
    new CompletionClose(FrameEvent.closeFrame(code, reason))
  }

  def create(serverSide: Boolean, closeTimeout: FiniteDuration, log: LoggingAdapter): Flow[Input, FrameStart, NotUsed] =
    Flow[Input].via(new FrameOutHandler(serverSide, closeTimeout, log)).named("frameOutHandler")
}
