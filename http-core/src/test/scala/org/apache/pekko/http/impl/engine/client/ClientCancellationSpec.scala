/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.client

import org.apache.pekko
import pekko.http.impl.util.{ ExampleHttpContexts, PekkoSpecWithMaterializer }
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{ headers, HttpRequest, HttpResponse }
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, ConnectionPoolSettings }
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber, Utils }

import scala.concurrent.Await
import scala.concurrent.duration._

class ClientCancellationSpec extends PekkoSpecWithMaterializer {
  "Http client connections" must {
    "support cancellation in simple outgoing connection" in Utils.assertAllStagesStopped(new TestSetup {
      testCase(
        Http().connectionTo(address.getHostName).toPort(address.getPort).http())
    })

    "support cancellation in pooled outgoing connection" in Utils.assertAllStagesStopped(new TestSetup {
      testCase(
        Flow[HttpRequest]
          .map((_, ()))
          .via(Http().cachedHostConnectionPool(address.getHostName, address.getPort))
          .map(_._1.get))
    })

    "support cancellation in simple outgoing connection with TLS" in Utils.assertAllStagesStopped(new TestSetup {
      pending
      testCase(
        Http().connectionTo("pekko.example.org")
          .withClientConnectionSettings(settingsWithProxyTransport)
          .withCustomHttpsConnectionContext(ExampleHttpContexts.exampleClientContext)
          .https())
    })

    "support cancellation in pooled outgoing connection with TLS" in Utils.assertAllStagesStopped(new TestSetup {
      testCase(
        Flow[HttpRequest]
          .map((_, ()))
          .via(Http().cachedHostConnectionPoolHttps("pekko.example.org", 443,
            settings = ConnectionPoolSettings(system).withConnectionSettings(settingsWithProxyTransport),
            connectionContext = ExampleHttpContexts.exampleClientContext))
          .map(_._1.get))
    })

  }

  class TestSetup {
    lazy val binding = Await.result(
      Http().newServerAt("localhost", 0).bindSync { _ => HttpResponse(headers = headers.Connection("close") :: Nil) },
      5.seconds)
    lazy val address = binding.localAddress

    lazy val bindingTls = Await.result(
      Http().newServerAt("localhost", 0).enableHttps(ExampleHttpContexts.exampleServerContext).bindSync { _ =>
        HttpResponse()
      },
      5.seconds)
    lazy val addressTls = bindingTls.localAddress

    def testCase(connection: Flow[HttpRequest, HttpResponse, Any]): Unit = {
      val requests = TestPublisher.probe[HttpRequest]()
      val responses = TestSubscriber.probe[HttpResponse]()
      Source.fromPublisher(requests).via(connection).runWith(Sink.fromSubscriber(responses))
      responses.request(1)
      requests.sendNext(HttpRequest())
      responses.expectNext().entity.dataBytes.runWith(Sink.cancelled)
      responses.cancel()
      requests.expectCancellation()

      binding.terminate(1.second)
      bindingTls.terminate(1.second)

      Http().shutdownAllConnectionPools()
    }

    def settingsWithProxyTransport: ClientConnectionSettings =
      ClientConnectionSettings(system)
        .withTransport(ExampleHttpContexts.proxyTransport(addressTls))
  }
}
