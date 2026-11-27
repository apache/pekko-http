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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.engine.client.OutgoingConnectionBlueprint.ResponseParsingMerge
import pekko.http.impl.engine.parsing.HttpResponseParser.ResponseContext
import pekko.http.impl.engine.parsing.ParserOutput.{ EntityStreamError, MessageEnd, ResponseStart, StrictEntityCreator }
import pekko.http.impl.engine.parsing.{ HttpHeaderParser, HttpResponseParser, ParserOutput }
import pekko.http.impl.util._
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.settings.ParserSettings
import pekko.stream.TLSProtocol.SessionBytes
import pekko.stream.scaladsl.{ GraphDSL, RunnableGraph, Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.stream.{ Attributes, ClosedShape, SourceShape }
import pekko.testkit._
import pekko.util.ByteString

class ResponseParsingMergeSpec extends PekkoSpec {

  val parserSettings = ParserSettings(system)

  "The ResponseParsingMerge stage" should {

    for (firstMethod <- List(HttpMethods.GET, HttpMethods.HEAD))
      s"keep a 204 and the following 200 separate in one input element (${firstMethod.value}, GET)" in {
        val body = ByteString("next response")
        val date = "Date: Mon, 01 Jan 2024 00:00:00 GMT"
        val firstHeaders = s"HTTP/1.1 204 No Content\r\n$date\r\n\r\n"
        val secondHeaders = s"HTTP/1.1 200 OK\r\n$date\r\nContent-Length: ${body.length}\r\n\r\n"
        val bytes = ByteString(firstHeaders + secondHeaders) ++ body
        val contexts = Source(List(ResponseContext(firstMethod, None), ResponseContext(HttpMethods.GET, None)))
        // Keep the input open: the second response must come from buffered bytes, not from a new element or EOF.
        val network = Source.single(SessionBytes(null, bytes)).concat(Source.maybe[SessionBytes])
        val parser = new HttpResponseParser(parserSettings, HttpHeaderParser(parserSettings, log))

        val outputs = Source.fromGraph(GraphDSL.create() { implicit b =>
          import GraphDSL.Implicits._
          val parsingMerge = b.add(new ResponseParsingMerge(parser))
          contexts ~> parsingMerge.in1
          network  ~> parsingMerge.in0
          SourceShape(parsingMerge.out)
        }).completionTimeout(3.seconds.dilated).runWith(Sink.seq).awaitResult(5.seconds.dilated)

        outputs.flatten.toList match {
          case (first: ResponseStart) :: MessageEnd :: (second: ResponseStart) :: MessageEnd :: Nil =>
            first.statusCode shouldBe StatusCodes.NoContent
            first.protocol shouldBe HttpProtocols.`HTTP/1.1`
            first.createEntity shouldBe StrictEntityCreator(HttpEntity.Empty)
            first.closeRequested shouldBe false

            second.statusCode shouldBe StatusCodes.OK
            second.protocol shouldBe HttpProtocols.`HTTP/1.1`
            val expectedEntity = HttpEntity.Strict(ContentTypes.`application/octet-stream`, body)
            second.createEntity shouldBe StrictEntityCreator(expectedEntity)
            second.closeRequested shouldBe false
          case unexpected => fail(s"Expected two separate final responses, got $unexpected")
        }
      }

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
          |01234""".stripMarginWithNewline("\r\n"))))

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
          |012345678""".stripMarginWithNewline("\r\n"))))

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
