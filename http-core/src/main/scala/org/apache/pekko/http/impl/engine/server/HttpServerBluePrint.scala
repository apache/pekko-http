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

package org.apache.pekko.http.impl.engine.server

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration.{ Deadline, Duration, FiniteDuration }
import scala.collection.immutable
import scala.util.control.{ NoStackTrace, NonFatal }
import org.apache.pekko
import pekko.NotUsed
import pekko.actor.Cancellable
import pekko.annotation.InternalApi
import pekko.dispatch.ExecutionContexts
import pekko.japi.Function
import pekko.event.LoggingAdapter
import pekko.http.ParsingErrorHandler
import pekko.util.ByteString
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.scaladsl._
import pekko.stream.stage._
import pekko.http.scaladsl.settings.ServerSettings
import pekko.http.impl.engine.parsing.ParserOutput._
import pekko.http.impl.engine.parsing._
import pekko.http.impl.engine.rendering.ResponseRenderingContext.CloseRequested
import pekko.http.impl.engine.rendering.{
  DateHeaderRendering,
  HttpResponseRendererFactory,
  ResponseRenderingContext,
  ResponseRenderingOutput
}
import pekko.http.impl.util._
import pekko.http.scaladsl.util.FastFuture.EnhancedFuture
import pekko.http.scaladsl.{ Http, TimeoutAccess }
import pekko.http.scaladsl.model.headers.`Timeout-Access`
import pekko.http.javadsl.model
import pekko.http.scaladsl.model._
import pekko.http.impl.util.LogByteStringTools._

import scala.annotation.nowarn
import scala.util.Failure

/**
 * INTERNAL API
 *
 * HTTP pipeline setup (without the underlying SSL/TLS (un)wrapping and the websocket switch):
 *
 *                 +----------+          +-------------+          +-------------+             +-----------+
 *    HttpRequest  |          |   Http-  |  request-   | Request- |             |   Request-  | request-  | ByteString
 *  | <------------+          <----------+ Preparation <----------+             <-------------+  Parsing  <-----------
 *  |              |          |  Request |             | Output   |             |   Output    |           |
 *  |              |          |          +-------------+          |             |             +-----------+
 *  |              |          |                                   |             |
 *  | Application- | One2One- |                                   | controller- |
 *  | Flow         |   Bidi   |                                   |    Stage    |
 *  |              |          |                                   |             |
 *  |              |          |                                   |             |             +-----------+
 *  | HttpResponse |          |           HttpResponse            |             |  Response-  | renderer- | ByteString
 *  v ------------->          +----------------------------------->             +-------------> Pipeline  +---------->
 *                 |          |                                   |             |  Rendering- |           |
 *                 +----------+                                   +-------------+  Context    +-----------+
 */
@InternalApi
private[http] object HttpServerBluePrint {
  def apply(settings: ServerSettings, log: LoggingAdapter, isSecureConnection: Boolean,
      dateHeaderRendering: DateHeaderRendering): Http.ServerLayer =
    userHandlerGuard(settings.pipeliningLimit).atop(
      requestTimeoutSupport(settings.timeouts.requestTimeout, log)).atop(
      requestPreparation(settings)).atop(
      controller(settings, log)).atop(
      parsingRendering(settings, log, isSecureConnection, dateHeaderRendering)).atop(
      websocketSupport(settings, log)).atop(
      tlsSupport).atop(
      logTLSBidiBySetting("server-plain-text", settings.logUnencryptedNetworkBytes))

  val tlsSupport: BidiFlow[ByteString, SslTlsOutbound, SslTlsInbound, SessionBytes, NotUsed] =
    BidiFlow.fromFlows(Flow[ByteString].map(SendBytes(_)), Flow[SslTlsInbound].collect { case x: SessionBytes => x })

  def websocketSupport(settings: ServerSettings, log: LoggingAdapter)
      : BidiFlow[ResponseRenderingOutput, ByteString, SessionBytes, SessionBytes, NotUsed] =
    BidiFlow.fromGraph(new ProtocolSwitchStage(settings, log))

  def parsingRendering(settings: ServerSettings, log: LoggingAdapter, isSecureConnection: Boolean,
      dateHeaderRendering: DateHeaderRendering)
      : BidiFlow[ResponseRenderingContext, ResponseRenderingOutput, SessionBytes, RequestOutput, NotUsed] =
    BidiFlow.fromFlows(rendering(settings, log, dateHeaderRendering), parsing(settings, log, isSecureConnection))

  def controller(settings: ServerSettings, log: LoggingAdapter)
      : BidiFlow[HttpResponse, ResponseRenderingContext, RequestOutput, RequestOutput, NotUsed] =
    BidiFlow.fromGraph(new ControllerStage(settings, log)).reversed

  def requestPreparation(
      settings: ServerSettings): BidiFlow[HttpResponse, HttpResponse, RequestOutput, HttpRequest, NotUsed] =
    BidiFlow.fromFlows(Flow[HttpResponse], new PrepareRequests(settings))

  def requestTimeoutSupport(
      timeout: Duration, log: LoggingAdapter): BidiFlow[HttpResponse, HttpResponse, HttpRequest, HttpRequest, NotUsed] =
    if (timeout == Duration.Zero) BidiFlow.identity[HttpResponse, HttpRequest]
    else BidiFlow.fromGraph(new RequestTimeoutSupport(timeout, log)).reversed

  /**
   * Two state stage, either transforms an incoming RequestOutput into a HttpRequest with strict entity and then pushes
   * that (the "idle" inHandler) or creates a HttpRequest with a streamed entity and switch to a state which will push
   * incoming chunks into the streaming entity until end of request is reached (the StreamedEntityCreator case in create
   * entity).
   */
  final class PrepareRequests(settings: ServerSettings) extends GraphStage[FlowShape[RequestOutput, HttpRequest]] {
    val in = Inlet[RequestOutput]("PrepareRequests.in")
    val out = Outlet[HttpRequest]("PrepareRequests.out")
    override val shape: FlowShape[RequestOutput, HttpRequest] = FlowShape.of(in, out)

    override def createLogic(inheritedAttributes: Attributes) =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        val remoteAddressOpt = inheritedAttributes.get[HttpAttributes.RemoteAddress].map(_.address)

        var downstreamPullWaiting = false
        var completionDeferred = false
        var entitySource: SubSourceOutlet[RequestOutput] = _

        // optimization: to avoid allocations the "idle" case in and out handlers are put directly on the GraphStageLogic itself
        override def onPull(): Unit = {
          pull(in)
        }

        // optimization: this callback is used to handle entity substream cancellation to avoid allocating a dedicated handler
        override def onDownstreamFinish(cause: Throwable): Unit = {
          if (entitySource ne null) {
            // application layer has cancelled or only partially consumed response entity:
            // connection will be closed
            entitySource.complete()
          }
          completeStage()
        }

        override def onUpstreamFinish(): Unit = super.onUpstreamFinish()
        override def onUpstreamFailure(ex: Throwable): Unit = {
          if (entitySource ne null) {
            // application layer has cancelled or only partially consumed response entity:
            // connection will be closed
            entitySource.fail(ex)
          }
          super.onUpstreamFailure(ex)
        }

        override def onPush(): Unit = grab(in) match {
          case RequestStart(method, uri, protocol, attrs, hdrs, entityCreator, _, _) =>
            val effectiveMethod = if (method == HttpMethods.HEAD && settings.transparentHeadRequests) HttpMethods.GET
            else method

            @nowarn("msg=use remote-address-attribute instead")
            val effectiveHeaders =
              if (settings.remoteAddressHeader && remoteAddressOpt.isDefined)
                headers.`Remote-Address`(RemoteAddress(remoteAddressOpt.get)) +: hdrs
              else hdrs

            val entity = createEntity(entityCreator).withSizeLimit(settings.parserSettings.maxContentLength)
            val httpRequest = HttpRequest(effectiveMethod, uri, effectiveHeaders, entity, protocol)
              .withAttributes(attrs)

            val effectiveHttpRequest = if (settings.remoteAddressAttribute) {
              remoteAddressOpt.fold(httpRequest) { remoteAddress =>
                httpRequest.addAttribute(AttributeKeys.remoteAddress, RemoteAddress(remoteAddress))
              }
            } else httpRequest

            push(out, effectiveHttpRequest)
          case other =>
            throw new IllegalStateException(s"unexpected element of type ${other.getClass}")
        }

        setIdleHandlers()

        def setIdleHandlers(): Unit = {
          if (completionDeferred) {
            completeStage()
          } else {
            setHandler(in, this)
            setHandler(out, this)
            if (downstreamPullWaiting) {
              downstreamPullWaiting = false
              pull(in)
            }
          }
        }

        def createEntity(creator: EntityCreator[RequestOutput, RequestEntity]): RequestEntity =
          creator match {
            case StrictEntityCreator(entity)    => entity
            case StreamedEntityCreator(creator) => streamRequestEntity(creator)
          }

        def streamRequestEntity(
            creator: (Source[ParserOutput.RequestOutput, NotUsed]) => RequestEntity): RequestEntity = {
          // stream incoming chunks into the request entity until we reach the end of it
          // and then toggle back to "idle"

          entitySource = new SubSourceOutlet[RequestOutput]("EntitySource")
          // optimization: re-use the idle outHandler
          entitySource.setHandler(this)

          // optimization: handlers are combined to reduce allocations
          val chunkedRequestHandler = new InHandler with OutHandler {
            def onPush(): Unit = {
              grab(in) match {
                case MessageEnd =>
                  entitySource.complete()
                  entitySource = null
                  setIdleHandlers()

                case x => entitySource.push(x)
              }
            }
            override def onUpstreamFinish(): Unit = {
              entitySource.complete()
              completeStage()
            }
            override def onUpstreamFailure(ex: Throwable): Unit = {
              entitySource.fail(ex)
              failStage(ex)
            }
            override def onPull(): Unit = {
              // remember this until we are done with the chunked entity
              // so can pull downstream then
              downstreamPullWaiting = true
            }
            override def onDownstreamFinish(cause: Throwable): Unit = {
              // downstream signalled not wanting any more requests
              // we should keep processing the entity stream and then
              // when it completes complete the stage
              completionDeferred = true
            }
          }

          setHandler(in, chunkedRequestHandler)
          setHandler(out, chunkedRequestHandler)
          creator(Source.fromGraph(entitySource.source))
        }

      }
  }

  def parsing(settings: ServerSettings, log: LoggingAdapter, isSecureConnection: Boolean)
      : Flow[SessionBytes, RequestOutput, NotUsed] = {
    import settings._

    // the initial header parser we initially use for every connection,
    // will not be mutated, all "shared copy" parsers copy on first-write into the header cache
    val rootParser = new HttpRequestParser(parserSettings, websocketSettings, rawRequestUriHeader,
      HttpHeaderParser(parserSettings, log))

    def establishAbsoluteUri(requestOutput: RequestOutput): RequestOutput = requestOutput match {
      case connect: RequestStart if connect.method == HttpMethods.CONNECT =>
        MessageStartError(StatusCodes.BadRequest,
          ErrorInfo(s"CONNECT requests are not supported", s"Rejecting CONNECT request to '${connect.uri}'"))
      case start: RequestStart =>
        try {
          val effectiveUri = HttpRequest.effectiveUri(start.uri, start.headers, isSecureConnection, defaultHostHeader)
          start.copy(uri = effectiveUri)
        } catch {
          case e: IllegalUriException =>
            MessageStartError(StatusCodes.BadRequest, e.info)
        }
      case x => x
    }

    Flow[SessionBytes].via(rootParser).map(establishAbsoluteUri)
  }

  def rendering(settings: ServerSettings, log: LoggingAdapter, dateHeaderRendering: DateHeaderRendering)
      : Flow[ResponseRenderingContext, ResponseRenderingOutput, NotUsed] = {
    import settings._

    val responseRendererFactory =
      new HttpResponseRendererFactory(serverHeader, responseHeaderSizeHint, log, dateHeaderRendering)

    Flow[ResponseRenderingContext]
      .via(responseRendererFactory.renderer.named("renderer"))
  }

  class RequestTimeoutSupport(initialTimeout: Duration, log: LoggingAdapter)
      extends GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {
    private val requestIn = Inlet[HttpRequest]("RequestTimeoutSupport.requestIn")
    private val requestOut = Outlet[HttpRequest]("RequestTimeoutSupport.requestOut")
    private val responseIn = Inlet[HttpResponse]("RequestTimeoutSupport.responseIn")
    private val responseOut = Outlet[HttpResponse]("RequestTimeoutSupport.responseOut")

    override def initialAttributes = Attributes.name("RequestTimeoutSupport")

    val shape = new BidiShape(requestIn, requestOut, responseIn, responseOut)

    def createLogic(effectiveAttributes: Attributes) = new GraphStageLogic(shape) {
      var openTimeouts = immutable.Queue[TimeoutAccessImpl]()
      // the application response might has already arrived after we scheduled the timeout response (which is close but ok)
      // or current head (same reason) is not for response the timeout has been scheduled for
      val callback: AsyncCallback[(TimeoutAccess, HttpResponse)] = getAsyncCallback {
        case (timeout, response) =>
          if (openTimeouts.headOption.exists(_ eq timeout)) {
            emit(responseOut, response, () => completeStage())
          }
      }
      setHandler(requestIn,
        new InHandler {
          def onPush(): Unit = {
            val request = grab(requestIn)
            val (entity, requestEnd) = HttpEntity.captureTermination(request.entity)
            val access = new TimeoutAccessImpl(request, initialTimeout, requestEnd, callback,
              interpreter.materializer, log)
            openTimeouts = openTimeouts.enqueue(access)
            push(requestOut, request.addHeader(`Timeout-Access`(access)).withEntity(entity))
          }
          override def onUpstreamFinish(): Unit = complete(requestOut)
          override def onUpstreamFailure(ex: Throwable): Unit = fail(requestOut, ex)
        })
      // TODO: provide and use default impl for simply connecting an input and an output port as we do here
      setHandler(requestOut,
        new OutHandler {
          def onPull(): Unit = pull(requestIn)
          override def onDownstreamFinish(cause: Throwable): Unit = cancel(requestIn)
        })
      setHandler(responseIn,
        new InHandler {
          def onPush(): Unit = {
            openTimeouts.head.clear()
            openTimeouts = openTimeouts.tail
            push(responseOut, grab(responseIn))
          }
          override def onUpstreamFinish(): Unit = complete(responseOut)
          override def onUpstreamFailure(ex: Throwable): Unit = fail(responseOut, ex)
        })
      setHandler(responseOut,
        new OutHandler {
          def onPull(): Unit = pull(responseIn)
          override def onDownstreamFinish(cause: Throwable): Unit = cancel(responseIn)
        })
    }
  }

  private class TimeoutSetup(
      val timeoutBase: Deadline,
      val scheduledTask: Cancellable,
      val timeout: Duration,
      val handler: HttpRequest => HttpResponse)

  private object DummyCancellable extends Cancellable {
    override def isCancelled: Boolean = true
    override def cancel(): Boolean = true
  }

  private class TimeoutAccessImpl(request: HttpRequest, initialTimeout: Duration, requestEnd: Future[Unit],
      trigger: AsyncCallback[(TimeoutAccess, HttpResponse)],
      materializer: Materializer, log: LoggingAdapter)
      extends AtomicReference[Future[TimeoutSetup]] with TimeoutAccess with (HttpRequest => HttpResponse) { self =>
    import materializer.executionContext

    private var currentTimeout = initialTimeout

    initialTimeout match {
      case timeout: FiniteDuration => set {
          requestEnd.fast.map(_ => new TimeoutSetup(Deadline.now, schedule(timeout, this), timeout, this))
        }
      case _ => set {
          requestEnd.fast.map(_ => new TimeoutSetup(Deadline.now, DummyCancellable, Duration.Inf, this))
        }
    }

    override def apply(request: HttpRequest) = {
      log.info("Request timeout encountered for request [{}]", request.debugString)
      // #default-request-timeout-httpresponse
      HttpResponse(StatusCodes.ServiceUnavailable,
        entity = "The server was not able " +
          "to produce a timely response to your request.\r\nPlease try again in a short while!")
      // #default-request-timeout-httpresponse
    }

    def clear(): Unit = // best effort timeout cancellation
      get.fast.foreach(setup => if (setup.scheduledTask ne null) setup.scheduledTask.cancel())

    override def updateTimeout(timeout: Duration): Unit = update(timeout, null: HttpRequest => HttpResponse)
    override def updateHandler(handler: HttpRequest => HttpResponse): Unit = update(null, handler)
    override def update(timeout: Duration, handler: HttpRequest => HttpResponse): Unit = {
      val promise = Promise[TimeoutSetup]()
      for (old <- getAndSet(promise.future).fast)
        promise.success {
          if ((old.scheduledTask eq null) || old.scheduledTask.cancel()) {
            val newHandler = if (handler eq null) old.handler else handler
            val newTimeout = if (timeout eq null) old.timeout else timeout
            val newScheduling = newTimeout match {
              case x: FiniteDuration => schedule(old.timeoutBase + x - Deadline.now, newHandler)
              case _                 => null // don't schedule a new timeout
            }
            currentTimeout = newTimeout
            new TimeoutSetup(old.timeoutBase, newScheduling, newTimeout, newHandler)
          } else old // too late, the previously set timeout cannot be cancelled anymore
        }
    }
    private def schedule(delay: FiniteDuration, handler: HttpRequest => HttpResponse): Cancellable =
      materializer.scheduleOnce(delay, new Runnable { def run() = trigger.invoke((self, handler(request))) })

    import pekko.http.impl.util.JavaMapping.Implicits._

    /** JAVA API * */
    def update(timeout: Duration, handler: Function[model.HttpRequest, model.HttpResponse]): Unit =
      update(timeout, handler(_: HttpRequest).asScala)
    def updateHandler(handler: Function[model.HttpRequest, model.HttpResponse]): Unit =
      updateHandler(handler(_: HttpRequest).asScala)

    def timeout = currentTimeout
  }

  class ControllerStage(settings: ServerSettings, log: LoggingAdapter)
      extends GraphStage[BidiShape[RequestOutput, RequestOutput, HttpResponse, ResponseRenderingContext]] {
    private val requestParsingIn = Inlet[RequestOutput]("ControllerStage.requestParsingIn")
    private val requestPrepOut = Outlet[RequestOutput]("ControllerStage.requestPrepOut")
    private val httpResponseIn = Inlet[HttpResponse]("ControllerStage.httpResponseIn")
    private val responseCtxOut = Outlet[ResponseRenderingContext]("ControllerStage.responseCtxOut")

    override def initialAttributes = Attributes.name("ControllerStage")

    val shape = new BidiShape(requestParsingIn, requestPrepOut, httpResponseIn, responseCtxOut)

    override private[pekko] def createLogicAndMaterializedValue(inheritedAttributes: Attributes,
        outerMaterializer: Materializer) =
      new GraphStageLogic(shape) {
        val parsingErrorHandler: ParsingErrorHandler =
          settings.parsingErrorHandlerInstance(ActorMaterializerHelper.downcast(outerMaterializer).system)
        val pullHttpResponseIn = () => tryPull(httpResponseIn)
        var openRequests = immutable.Queue[RequestStart]()
        var oneHundredContinueResponsePending = false
        var pullSuppressed = false
        var messageEndPending = false

        setHandler(requestParsingIn,
          new InHandler {
            def onPush(): Unit =
              grab(requestParsingIn) match {
                case r: RequestStart =>
                  openRequests = openRequests.enqueue(r)
                  messageEndPending = r.createEntity.isInstanceOf[StreamedEntityCreator[_, _]]
                  val rs = if (r.expect100Continue) {
                    r.createEntity match {
                      case StrictEntityCreator(HttpEntity.Strict(_, _)) =>
                        // This covers two cases:
                        // - Either: The strict entity got all its data send already, so no need to wait for more data
                        // - Or: The strict entity contains no data (Content-Length header value was 0 or it did not exist), the client will not send any data
                        r
                      case _ =>
                        oneHundredContinueResponsePending = true
                        r.copy(createEntity = with100ContinueTrigger(r.createEntity))
                    }
                  } else r
                  push(requestPrepOut, rs)
                case MessageEnd =>
                  messageEndPending = false
                  push(requestPrepOut, MessageEnd)
                case MessageStartError(status, info)                                   => finishWithIllegalRequestError(status, info)
                case x: EntityStreamError if messageEndPending && openRequests.isEmpty =>
                  // client terminated the connection after receiving an early response to 100-continue
                  completeStage()
                case x =>
                  push(requestPrepOut, x)
              }
            override def onUpstreamFinish() =
              if (openRequests.isEmpty) completeStage()
              else complete(requestPrepOut)
          })

        setHandler(requestPrepOut,
          new OutHandler {
            def onPull(): Unit =
              if (oneHundredContinueResponsePending) pullSuppressed = true
              else if (!hasBeenPulled(requestParsingIn)) pull(requestParsingIn)
            override def onDownstreamFinish(cause: Throwable): Unit =
              if (openRequests.isEmpty) completeStage()
              else failStage(
                new IllegalStateException("User handler flow was cancelled with ongoing request") with NoStackTrace)
          })

        setHandler(httpResponseIn,
          new InHandler {
            def onPush(): Unit = {
              val requestStart = openRequests.head
              openRequests = openRequests.tail

              val response0 = grab(httpResponseIn)
              val response =
                if (response0.entity.isStrict) response0 // response stream cannot fail
                else response0.mapEntity { e =>
                  val (newEntity, fut) = HttpEntity.captureTermination(e)
                  fut.onComplete {
                    case Failure(ex) =>
                      log.error(ex,
                        s"Response stream for [${requestStart.debugString}] failed with '${ex.getMessage}'. Aborting connection.")
                    case _ => // ignore
                  }(ExecutionContexts.parasitic)
                  newEntity
                }

              val isEarlyResponse = messageEndPending && openRequests.isEmpty
              if (isEarlyResponse && response.status.isSuccess)
                log.warning(
                  s"Sending an 2xx 'early' response before end of request for ${requestStart.uri} received... " +
                  "Note that the connection will be closed after this response. Also, many clients will not read early responses! " +
                  "Consider only issuing this response after the request data has been completely read!")
              val forceClose =
                (requestStart.expect100Continue && oneHundredContinueResponsePending) ||
                (isClosed(requestParsingIn) && openRequests.isEmpty) ||
                isEarlyResponse

              val close =
                if (forceClose) CloseRequested.ForceClose
                else if (requestStart.closeRequested) CloseRequested.RequestAskedForClosing
                else CloseRequested.Unspecified

              emit(responseCtxOut,
                ResponseRenderingContext(response, requestStart.method, requestStart.protocol, close),
                pullHttpResponseIn)
              if (!isClosed(requestParsingIn) && close.shouldClose && requestStart.expect100Continue)
                maybePullRequestParsingIn()
            }
            override def onUpstreamFinish() =
              if (openRequests.isEmpty && isClosed(requestParsingIn)) completeStage()
              else complete(responseCtxOut)
            override def onUpstreamFailure(ex: Throwable): Unit =
              ex match {
                case EntityStreamException(errorInfo) =>
                  // the application has forwarded a request entity stream error to the response stream
                  finishWithIllegalRequestError(StatusCodes.BadRequest, errorInfo)

                case EntityStreamSizeException(limit, contentLength) =>
                  val summary = contentLength match {
                    case Some(cl) => s"Request Content-Length of $cl bytes exceeds the configured limit of $limit bytes"
                    case None =>
                      s"Aggregated data length of request entity exceeds the configured limit of $limit bytes"
                  }
                  val info =
                    ErrorInfo(summary, "Consider increasing the value of pekko.http.server.parsing.max-content-length")
                  finishWithIllegalRequestError(StatusCodes.ContentTooLarge, info)

                case IllegalUriException(errorInfo) =>
                  finishWithIllegalRequestError(StatusCodes.BadRequest, errorInfo)

                case ex: ServerTerminationDeadlineReached => failStage(ex)

                case NonFatal(e) =>
                  log.error(e, "Internal server error, sending 500 response")
                  emitErrorResponse(HttpResponse(StatusCodes.InternalServerError))
              }
          })

        setHandler(responseCtxOut,
          new OutHandler {
            override def onPull() = {
              pull(httpResponseIn)
              // after the initial pull here we only ever pull after having emitted in `onPush` of `httpResponseIn`
              setHandler(responseCtxOut, GraphStageLogic.EagerTerminateOutput)
            }
          })

        def finishWithIllegalRequestError(status: StatusCode, info: ErrorInfo): Unit = {
          val errorResponse = JavaMapping.toScala(parsingErrorHandler.handle(status, info, log, settings))
          emitErrorResponse(errorResponse)
        }

        def emitErrorResponse(response: HttpResponse): Unit =
          emit(responseCtxOut, ResponseRenderingContext(response, closeRequested = CloseRequested.ForceClose),
            () => completeStage())

        def maybePullRequestParsingIn(): Unit =
          if (pullSuppressed) {
            pullSuppressed = false
            pull(requestParsingIn)
          }

        /**
         * The `Expect: 100-continue` header has a special status in HTTP.
         * It allows the client to send an `Expect: 100-continue` header with the request and then pause request sending
         * (i.e. hold back sending the request entity). The server reads the request headers, determines whether it wants to
         * accept the request and responds with
         *
         * - `417 Expectation Failed`, if it doesn't support the `100-continue` expectation
         * (or if the `Expect` header contains other, unsupported expectations).
         * - a `100 Continue` response,
         * if it is ready to accept the request entity and the client should go ahead with sending it
         * - a final response (like a 4xx to signal some client-side error
         * (e.g. if the request entity length is beyond the configured limit) or a 3xx redirect)
         *
         * Only if the client receives a `100 Continue` response from the server is it allowed to continue sending the request
         * entity. In this case it will receive another response after having completed request sending.
         * So this special feature breaks the normal "one request - one response" logic of HTTP!
         * It therefore requires special handling in all HTTP stacks (client- and server-side).
         *
         * For us this means:
         *
         * - on the server-side:
         * After having read a `Expect: 100-continue` header with the request we package up an `HttpRequest` instance and send
         * it through to the application. Only when (and if) the application then requests data from the entity stream do we
         * send out a `100 Continue` response and continue reading the request entity.
         * The application can therefore determine itself whether it wants the client to send the request entity
         * by deciding whether to look at the request entity data stream or not.
         * If the application sends a response *without* having looked at the request entity the client receives this
         * response *instead of* the `100 Continue` response and the server closes the connection afterwards.
         *
         * - on the client-side:
         * If the user adds a `Expect: 100-continue` header to the request we need to hold back sending the entity until
         * we've received a `100 Continue` response.
         */
        val emit100ContinueResponse =
          getAsyncCallback[Unit] { _ =>
            oneHundredContinueResponsePending = false
            emit(responseCtxOut, ResponseRenderingContext(HttpResponse(StatusCodes.Continue)))
            maybePullRequestParsingIn()
          }

        case object OneHundredContinueStage extends GraphStage[FlowShape[ParserOutput, ParserOutput]] {
          val in: Inlet[ParserOutput] = Inlet("OneHundredContinueStage.in")
          val out: Outlet[ParserOutput] = Outlet("OneHundredContinueStage.out")
          override val shape: FlowShape[ParserOutput, ParserOutput] = FlowShape(in, out)

          override def initialAttributes = Attributes.name("expect100continueTrigger")

          override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
            new GraphStageLogic(shape) with InHandler with OutHandler {
              private var oneHundredContinueSent = false

              override def onPush(): Unit = push(out, grab(in))
              override def onPull(): Unit = {
                if (!oneHundredContinueSent) {
                  oneHundredContinueSent = true
                  emit100ContinueResponse.invoke(())
                }
                pull(in)
              }

              setHandlers(in, out, this)
            }
        }

        def with100ContinueTrigger[T <: ParserOutput](createEntity: EntityCreator[T, RequestEntity]) =
          StreamedEntityCreator {
            createEntity.compose[Source[T, NotUsed]] {
              _.via(OneHundredContinueStage.asInstanceOf[GraphStage[FlowShape[T, T]]])
            }
          }
      } -> NotUsed

    def createLogic(effectiveAttributes: Attributes): GraphStageLogic =
      throw new IllegalStateException("unexpected invocation")
  }

  /**
   * Ensures that the user handler
   *  - produces exactly one response per request
   *  - has not more than `pipeliningLimit` responses outstanding
   */
  def userHandlerGuard(pipeliningLimit: Int): BidiFlow[HttpResponse, HttpResponse, HttpRequest, HttpRequest, NotUsed] =
    One2OneBidiFlow[HttpRequest, HttpResponse](pipeliningLimit).reversed

  private class ProtocolSwitchStage(settings: ServerSettings, log: LoggingAdapter)
      extends GraphStage[BidiShape[ResponseRenderingOutput, ByteString, SessionBytes, SessionBytes]] {

    private val fromNet = Inlet[SessionBytes]("ProtocolSwitchStage.fromNet")
    private val toNet = Outlet[ByteString]("ProtocolSwitchStage.toNet")

    private val toHttp = Outlet[SessionBytes]("ProtocolSwitchStage.toHttp")
    private val fromHttp = Inlet[ResponseRenderingOutput]("ProtocolSwitchStage.fromHttp")

    override def initialAttributes = Attributes.name("ProtocolSwitchStage")

    override val shape = BidiShape(fromHttp, toNet, fromNet, toHttp)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {
      import pekko.http.impl.engine.rendering.ResponseRenderingOutput._

      /*
       * These handlers are in charge until a switch command comes in, then they
       * are replaced.
       */

      setHandler(fromHttp,
        new InHandler {
          override def onPush(): Unit =
            grab(fromHttp) match {
              case HttpData(b) => push(toNet, b)
              case SwitchToOtherProtocol(bytes, handlerFlow) =>
                push(toNet, bytes)
                complete(toHttp)
                cancel(fromHttp)
                switchToOtherProtocol(handlerFlow)
            }
          override def onUpstreamFinish(): Unit = complete(toNet)
          override def onUpstreamFailure(ex: Throwable): Unit = fail(toNet, ex)
        })
      setHandler(toNet,
        new OutHandler {
          override def onPull(): Unit = pull(fromHttp)
          override def onDownstreamFinish(cause: Throwable): Unit = completeStage()
        })

      setHandler(fromNet,
        new InHandler {
          override def onPush(): Unit = push(toHttp, grab(fromNet))
          override def onUpstreamFinish(): Unit = complete(toHttp)
          override def onUpstreamFailure(ex: Throwable): Unit = fail(toHttp, ex)
        })
      setHandler(toHttp,
        new OutHandler {
          override def onPull(): Unit = pull(fromNet)
          override def onDownstreamFinish(cause: Throwable): Unit = cancel(fromNet)
        })

      private var activeTimers = 0
      private def timeout = ActorMaterializerHelper.downcast(materializer).settings.subscriptionTimeoutSettings.timeout
      private def addTimeout(s: SubscriptionTimeout): Unit = {
        if (activeTimers == 0) setKeepGoing(true)
        activeTimers += 1
        scheduleOnce(s, timeout)
      }
      private def cancelTimeout(s: SubscriptionTimeout): Unit =
        if (isTimerActive(s)) {
          activeTimers -= 1
          if (activeTimers == 0) setKeepGoing(false)
          cancelTimer(s)
        }
      override def onTimer(timerKey: Any): Unit = timerKey match {
        case SubscriptionTimeout(f) =>
          activeTimers -= 1
          if (activeTimers == 0) setKeepGoing(false)
          f()
      }

      def switchToOtherProtocol(newFlow: Flow[ByteString, ByteString, Any]): Unit = {

        val sinkIn = new SubSinkInlet[ByteString]("FrameSink")
        sinkIn.setHandler(new InHandler {
          override def onPush(): Unit = push(toNet, sinkIn.grab())
          override def onUpstreamFinish(): Unit = complete(toNet)
          override def onUpstreamFailure(ex: Throwable): Unit = fail(toNet, ex)
        })

        if (isClosed(fromNet)) {
          setHandler(toNet,
            new OutHandler {
              override def onPull(): Unit = sinkIn.pull()
              override def onDownstreamFinish(cause: Throwable): Unit = {
                completeStage()
                sinkIn.cancel()
              }
            })
          newFlow.runWith(Source.empty, sinkIn.sink)(subFusingMaterializer)
        } else {
          val sourceOut = new SubSourceOutlet[ByteString]("FrameSource")

          val timeoutKey = SubscriptionTimeout(() => {
            sourceOut.timeout(timeout)
            if (sourceOut.isClosed) completeStage()
          })
          addTimeout(timeoutKey)

          setHandler(toNet,
            new OutHandler {
              override def onPull(): Unit = sinkIn.pull()
              override def onDownstreamFinish(cause: Throwable): Unit = {
                completeStage()
                sinkIn.cancel()
                sourceOut.complete()
              }
            })

          setHandler(fromNet,
            new InHandler {
              override def onPush(): Unit = {
                if (sourceOut.isAvailable) {
                  sourceOut.push(grab(fromNet).bytes)
                }
              }
              override def onUpstreamFinish(): Unit = sourceOut.complete()
              override def onUpstreamFailure(ex: Throwable): Unit = sourceOut.fail(ex)
            })
          sourceOut.setHandler(new OutHandler {
            override def onPull(): Unit = {
              // This check only needed on the first pull due to potential element
              // pushed in response to pull by previous source
              if (isAvailable(fromNet)) {
                sourceOut.push(grab(fromNet).bytes)
              } else if (!hasBeenPulled(fromNet)) {
                pull(fromNet)
              }
              cancelTimeout(timeoutKey)

              sourceOut.setHandler(new OutHandler {
                override def onPull(): Unit = if (!hasBeenPulled(fromNet)) pull(fromNet)
                override def onDownstreamFinish(cause: Throwable): Unit = cancel(fromNet)
              })
            }
            override def onDownstreamFinish(cause: Throwable): Unit = cancel(fromNet)
          })

          // disable the old handlers, at this point we might still get something due to cancellation delay which we need to ignore
          setHandlers(fromHttp, toHttp,
            new InHandler with OutHandler {
              override def onPush(): Unit = ()
              override def onPull(): Unit = ()
              override def onUpstreamFinish(): Unit = ()
              override def onUpstreamFailure(ex: Throwable): Unit = ()
              override def onDownstreamFinish(cause: Throwable): Unit = ()
            })

          newFlow.runWith(sourceOut.source, sinkIn.sink)(subFusingMaterializer)
        }
      }
    }
  }

  private case class SubscriptionTimeout(andThen: () => Unit)
}
