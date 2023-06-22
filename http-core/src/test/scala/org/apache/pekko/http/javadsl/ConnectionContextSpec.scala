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

package org.apache.pekko.http.javadsl

import java.util.{ Collections, Optional }
import javax.net.ssl.SSLContext

import org.apache.pekko.stream.TLSClientAuth
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConnectionContextSpec extends AnyWordSpec with Matchers {

  "ConnectionContext.https" should {

    "pass through all parameters" in {
      val sslContext = SSLContext.getDefault
      val ciphers: Optional[java.util.Collection[String]] = Optional.of(Collections.singletonList("A"))
      val protocols: Optional[java.util.Collection[String]] = Optional.of(Collections.singletonList("B"))
      val clientAuth = Optional.of(TLSClientAuth.need)
      val parameters = Optional.of(sslContext.getDefaultSSLParameters)

      val httpsContext =
        org.apache.pekko.http.javadsl.ConnectionContext.https(sslContext, ciphers, protocols, clientAuth, parameters)
      httpsContext.getSslContext should ===(sslContext)
      httpsContext.getEnabledCipherSuites.get.toArray.toList shouldBe (ciphers.get.toArray.toList)
      httpsContext.getEnabledProtocols.get.toArray.toList shouldBe (protocols.get.toArray.toList)
      httpsContext.getClientAuth should ===(clientAuth)
      httpsContext.getSslParameters should ===(parameters)
    }
  }
}
