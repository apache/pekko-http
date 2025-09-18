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

package org.apache.pekko.http.scaladsl.server

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.concurrent.duration.Duration
import scala.util.Try

import org.apache.pekko
import pekko.Done
import pekko.actor.ActorSystem
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.Http.ServerBinding
import pekko.http.scaladsl.client.RequestBuilding
import pekko.http.scaladsl.model.{ HttpRequest, StatusCodes }
import pekko.http.scaladsl.settings.ServerSettings
import pekko.testkit.EventFilter

import org.scalatest.concurrent.Eventually

import com.typesafe.config.ConfigFactory

class HttpAppSpec extends PekkoSpecWithMaterializer with RequestBuilding with Eventually {
  import system.dispatcher

  class MinimalApp extends HttpApp {

    val shutdownPromise = Promise[Done]()
    val bindingPromise = Promise[ServerBinding]()

    def shutdownServer(): Unit = shutdownPromise.success(Done)

    override protected def routes: Route =
      path("foo") {
        complete("bar")
      }

    override protected def postHttpBinding(binding: ServerBinding): Unit = {
      super.postHttpBinding(binding)
      bindingPromise.success(binding)
    }

    override protected def waitForShutdownSignal(system: ActorSystem)(implicit ec: ExecutionContext): Future[Done] = {
      shutdownPromise.future
    }
  }

  class SneakyServer extends MinimalApp {

    val postBindingCalled = new AtomicBoolean(false)
    val postBindingFailureCalled = new AtomicBoolean(false)
    val postShutdownCalled = new AtomicBoolean(false)

    override protected def postHttpBindingFailure(cause: Throwable): Unit = postBindingFailureCalled.set(true)

    override protected def postHttpBinding(binding: ServerBinding): Unit = {
      postBindingCalled.set(true)
      bindingPromise.success(binding)
    }

    override protected def postServerShutdown(attempt: Try[Done], system: ActorSystem): Unit =
      postShutdownCalled.set(true)
  }

  def withMinimal(testCode: MinimalApp => Any): Unit = {
    val minimal = new MinimalApp()
    try testCode(minimal)
    finally {
      if (!minimal.shutdownPromise.isCompleted) minimal.shutdownPromise.success(Done)
    }
  }

  def withSneaky(testCode: SneakyServer => Any): Unit = {
    val sneaky = new SneakyServer()
    try testCode(sneaky)
    finally {
      if (!sneaky.shutdownPromise.isCompleted) sneaky.shutdownPromise.success(Done)
    }
  }

  "HttpApp" should {

    "start only with host and port" in withMinimal { minimal =>
      val server = Future {
        minimal.startServer("localhost", 0)
      }

      val binding = Await.result(minimal.bindingPromise.future, Duration(5, TimeUnit.SECONDS))

      // Checking server is up and running
      callAndVerify(binding, "foo")

      // Requesting the server to shutdown
      minimal.shutdownServer()
      Await.ready(server, Duration(1, TimeUnit.SECONDS))
      server.isCompleted should ===(true)
    }

    "start without ActorSystem" in withMinimal { minimal =>
      val server = Future {
        minimal.startServer("localhost", 0, ServerSettings(ConfigFactory.load))
      }

      val binding = Await.result(minimal.bindingPromise.future, Duration(5, TimeUnit.SECONDS))

      // Checking server is up and running
      callAndVerify(binding, "foo")

      // Requesting the server to shutdown
      minimal.shutdownServer()
      Await.ready(server, Duration(1, TimeUnit.SECONDS))
      server.isCompleted should ===(true)

    }

    "start providing an ActorSystem" in withMinimal { minimal =>
      val server = Future {
        minimal.startServer("localhost", 0, system)
      }

      val binding = Await.result(minimal.bindingPromise.future, Duration(5, TimeUnit.SECONDS))

      // Checking server is up and running
      callAndVerify(binding, "foo")

      // Requesting the server to shutdown
      minimal.shutdownServer()
      Await.ready(server, Duration(1, TimeUnit.SECONDS))
      server.isCompleted should ===(true)
      system.whenTerminated.isCompleted should ===(false)

    }

    "start providing an ActorSystem and Settings" in withMinimal { minimal =>
      val server = Future {
        minimal.startServer("localhost", 0, ServerSettings(system), system)
      }

      val binding = Await.result(minimal.bindingPromise.future, Duration(5, TimeUnit.SECONDS))

      // Checking server is up and running
      callAndVerify(binding, "foo")

      // Requesting the server to shutdown
      minimal.shutdownServer()
      Await.ready(server, Duration(1, TimeUnit.SECONDS))
      server.isCompleted should ===(true)
      system.whenTerminated.isCompleted should ===(false)

    }

    "provide binding if available" in withMinimal { minimal =>
      minimal.binding().isFailure should ===(true)

      val server = Future {
        minimal.startServer("127.0.0.1", 0, ServerSettings(ConfigFactory.load))
      }

      val binding = Await.result(minimal.bindingPromise.future, Duration(5, TimeUnit.SECONDS))

      minimal.binding().isSuccess should ===(true)
      minimal.binding().get.localAddress.getAddress.getHostAddress should ===("127.0.0.1")

      // Checking server is up and running
      callAndVerify(binding, "foo")

      // Requesting the server to shutdown
      minimal.shutdownServer()
      Await.ready(server, Duration(1, TimeUnit.SECONDS))
      server.isCompleted should ===(true)

    }

    "notify" when {

      "shutting down" in withSneaky { sneaky =>
        val server = Future {
          sneaky.startServer("localhost", 0, ServerSettings(ConfigFactory.load))
        }

        val binding = Await.result(sneaky.bindingPromise.future, Duration(5, TimeUnit.SECONDS))

        sneaky.postShutdownCalled.get() should ===(false)

        // Checking server is up and running
        callAndVerify(binding, "foo")

        // Requesting the server to shutdown
        sneaky.shutdownServer()
        Await.ready(server, Duration(1, TimeUnit.SECONDS))
        server.isCompleted should ===(true)
        eventually {
          sneaky.postShutdownCalled.get() should ===(true)
        }

      }

      "after binding is successful" in withSneaky { sneaky =>
        val server = Future {
          sneaky.startServer("localhost", 0, ServerSettings(ConfigFactory.load))
        }

        val binding = Await.result(sneaky.bindingPromise.future, Duration(5, TimeUnit.SECONDS))

        sneaky.postBindingCalled.get() should ===(true)

        // Checking server is up and running
        callAndVerify(binding, "foo")

        // Requesting the server to shutdown
        sneaky.shutdownServer()
        Await.ready(server, Duration(1, TimeUnit.SECONDS))
        server.isCompleted should ===(true)

      }

      "after binding is unsuccessful" in withSneaky { sneaky =>
        val serverSocket = new ServerSocket()
        serverSocket.bind(new InetSocketAddress("127.0.0.1", 0))
        val port = serverSocket.getLocalPort

        try {
          EventFilter[SocketException](pattern = ".*Address already in use.*", occurrences = 1).intercept {
            sneaky.startServer("localhost", port, system)
          }

          eventually {
            sneaky.postBindingFailureCalled.get() should ===(true)
          }
        } finally serverSocket.close()
      }

    }

  }

  private def callAndVerify(binding: ServerBinding, path: String) = {
    val host = binding.localAddress.getHostString
    val port = binding.localAddress.getPort

    val request = HttpRequest(uri = s"http://$host:$port/$path")
    val response = Http().singleRequest(request)
    response.futureValue.status should ===(StatusCodes.OK)
  }
}
