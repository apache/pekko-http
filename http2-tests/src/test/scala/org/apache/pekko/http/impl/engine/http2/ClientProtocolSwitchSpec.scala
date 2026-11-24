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

import javax.net.ssl.{ SSLContext, SSLException }

import scala.concurrent.duration.Duration
import scala.concurrent.{ CanAwait, ExecutionContext, Future, Promise }
import scala.util.{ Success, Try }

import org.apache.pekko
import pekko.http.impl.engine.http2.ClientProtocolSwitch.ClientLayer
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, StatusCodes }
import pekko.stream.TLSProtocol.{ SendBytes, SessionBytes, SslTlsInbound, SslTlsOutbound }
import pekko.stream.scaladsl.{ BidiFlow, Flow, Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.util.ByteString

class ClientProtocolSwitchSpec extends PekkoSpecWithMaterializer {

  "The ClientProtocolSwitch" should {

    "deliver buffered inbound data and then completion if the connection closes while the installation is pending" in
    new TestSetup(new DecidedButNotDelivered(Success(Http2AlpnSupport.H2))) {
      // the switch takes one element before a layer is installed, so the completion reaches it right after
      netIn.sendNext(SessionBytes(session, ByteString("abc")))
      netIn.sendComplete()

      val response = responses.requestNext()
      response.status shouldBe Http2Status
      response.entity shouldBe HttpEntity(ByteString("abc"))
      responses.expectComplete()
    }

    "install the HTTP/1.1 layer if the connection closes while its installation is pending" in
    new TestSetup(new DecidedButNotDelivered(Success("http/1.1"))) {
      netIn.sendNext(SessionBytes(session, ByteString("abc")))
      netIn.sendComplete()

      responses.requestNext().status shouldBe Http1Status
      responses.expectComplete()
    }

    "fail if the connection closes before the ALPN protocol was negotiated" in
    new TestSetup(Promise[String]().future) {
      netIn.sendNext(SessionBytes(session, ByteString("abc")))
      netIn.sendComplete()

      responses.expectSubscriptionAndError() shouldBe an[SSLException]
    }
  }

  val Http1Status = StatusCodes.Accepted
  val Http2Status = StatusCodes.OK

  /** A stand-in for a client layer: turns each inbound element into a response with a status that identifies it. */
  def markerLayer(status: StatusCodes.Success): ClientLayer =
    BidiFlow.fromFlows(
      Flow[HttpRequest].map[SslTlsOutbound](_ => SendBytes(ByteString.empty)),
      Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) => HttpResponse(status, entity = HttpEntity(bytes)) })

  class TestSetup(negotiatedProtocol: Future[String]) {
    val session = SSLContext.getDefault.createSSLEngine.getSession

    val requests = TestPublisher.probe[HttpRequest]()
    val netIn = TestPublisher.probe[SslTlsInbound]()
    val netOut = TestSubscriber.probe[SslTlsOutbound]()
    val responses = TestSubscriber.probe[HttpResponse]()

    Source.fromPublisher(requests)
      .via(
        ClientProtocolSwitch(negotiatedProtocol, markerLayer(Http1Status), markerLayer(Http2Status))
          .join(Flow.fromSinkAndSource(Sink.fromSubscriber(netOut), Source.fromPublisher(netIn))))
      .runWith(Sink.fromSubscriber(responses))
  }

  /**
   * A future in the state the switch observes when the close of the connection overtakes the negotiation result:
   * the result is there, but the callback registered for it has not run. Here it never runs, so the outcome of a
   * test cannot depend on a race between the two.
   */
  class DecidedButNotDelivered(decision: Try[String]) extends Future[String] {
    override def onComplete[U](f: Try[String] => U)(implicit executor: ExecutionContext): Unit = ()
    override def isCompleted: Boolean = true
    override def value: Option[Try[String]] = Some(decision)
    override def transform[S](f: Try[String] => Try[S])(implicit executor: ExecutionContext): Future[S] =
      Promise[S]().future
    override def transformWith[S](f: Try[String] => Future[S])(implicit executor: ExecutionContext): Future[S] =
      Promise[S]().future
    override def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this
    override def result(atMost: Duration)(implicit permit: CanAwait): String = decision.get
  }
}
