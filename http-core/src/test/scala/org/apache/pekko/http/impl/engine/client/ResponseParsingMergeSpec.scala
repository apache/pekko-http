/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.client

import org.apache.pekko
import pekko.http.impl.engine.client.OutgoingConnectionBlueprint.ResponseParsingMerge
import pekko.http.impl.engine.parsing.HttpResponseParser.ResponseContext
import pekko.http.impl.engine.parsing.ParserOutput.EntityStreamError
import pekko.http.impl.engine.parsing.{ HttpHeaderParser, HttpResponseParser, ParserOutput }
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.settings.ParserSettings
import pekko.stream.TLSProtocol.SessionBytes
import pekko.stream.scaladsl.{ GraphDSL, RunnableGraph, Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.stream.{ Attributes, ClosedShape }
import pekko.testkit.PekkoSpec
import pekko.util.ByteString

class ResponseParsingMergeSpec extends PekkoSpec {

  val parserSettings = ParserSettings(system)

  "The ResponseParsingMerge stage" should {

    "not lose entity truncation errors on upstream finish" in {
      val inBypassProbe = TestPublisher.manualProbe[OutgoingConnectionBlueprint.BypassData]()
      val inSessionBytesProbe = TestPublisher.manualProbe[SessionBytes]()
      val responseProbe = TestSubscriber.manualProbe[List[ParserOutput.ResponseOutput]]()

      val responseParsingMerge: ResponseParsingMerge = {
        val rootParser = new HttpResponseParser(parserSettings, HttpHeaderParser(parserSettings, log))
        new ResponseParsingMerge(rootParser)
      }

      RunnableGraph.fromGraph(
        GraphDSL.create() { implicit b =>
          import GraphDSL.Implicits._
          val parsingMerge = b.add(responseParsingMerge)

          Source.fromPublisher(inBypassProbe)       ~> parsingMerge.in1
          Source.fromPublisher(inSessionBytesProbe) ~> parsingMerge.in0
          parsingMerge.out                          ~> Sink.fromSubscriber(responseProbe)

          ClosedShape
        }.withAttributes(Attributes.inputBuffer(1, 8))).run()

      val inSessionBytesSub = inSessionBytesProbe.expectSubscription()
      val inBypassSub = inBypassProbe.expectSubscription()
      val responseSub = responseProbe.expectSubscription()

      responseSub.request(1)
      inSessionBytesSub.expectRequest()
      inBypassSub.sendNext(ResponseContext(HttpMethods.GET, None))

      inSessionBytesSub.sendNext(SessionBytes(null,
        ByteString(
          """HTTP/1.1 200 OK
          |Transfer-Encoding: chunked
          |Connection: lalelu
          |Content-Type: application/pdf
          |Server: spray-can
          |
          |1
          |0
          |2
          |01
          |3
          |012
          |4
          |0123
          |5
          |01234""".stripMargin)))

      inSessionBytesSub.sendNext(SessionBytes(null,
        ByteString(
          """
          |6
          |012345
          |7
          |0123456
          |8
          |01234567
          |9
          |012345678""".stripMargin)))

      inSessionBytesSub.sendComplete()

      responseSub.request(2)
      val responseChunks = responseProbe.expectNextN(3).flatten
      responseProbe.expectComplete()

      responseChunks.last shouldBe an[EntityStreamError]
      responseChunks.last shouldEqual EntityStreamError(ErrorInfo(
        "Entity stream truncation. The HTTP parser was receiving an entity when the underlying connection was closed unexpectedly."))
    }

  }

}
