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

package docs.http.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.{ ConnectionContext, Http }
import docs.CompileOnlySpec
import javax.net.ssl.{ SSLContext, SSLEngine }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpsExamplesSpec extends AnyWordSpec with Matchers with CompileOnlySpec {

  "disable hostname verification for connection" in compileOnlySpec {
    val unsafeHost = "example.com"
    // #disable-hostname-verification-connection
    implicit val system = ActorSystem()

    def createInsecureSslEngine(host: String, port: Int): SSLEngine = {
      val engine = SSLContext.getDefault.createSSLEngine(host, port)
      engine.setUseClientMode(true)

      // WARNING: this creates an SSL Engine without enabling endpoint identification/verification procedures
      // Disabling host name verification is a very bad idea, please don't unless you have a very good reason to.
      // When in doubt, use the `ConnectionContext.httpsClient` that takes an `SSLContext` instead, or enable with:
      // engine.setSSLParameters({
      //  val params = engine.getSSLParameters
      //  params.setEndpointIdentificationAlgorithm("https")
      //  params
      // )

      engine
    }
    val badCtx = ConnectionContext.httpsClient(createInsecureSslEngine _)
    Http().outgoingConnectionHttps(unsafeHost, connectionContext = badCtx)
    // #disable-hostname-verification-connection
  }
}
