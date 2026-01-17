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
import pekko.http.impl.engine.client.OutgoingConnectionBlueprint.PrepareResponse
import pekko.http.impl.engine.parsing.ParserOutput
import pekko.http.impl.engine.parsing.ParserOutput.{
  EntityChunk, EntityStreamError, StreamedEntityCreator, StrictEntityCreator
}
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.settings.ParserSettings
import pekko.stream.Attributes
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.util.ByteString
import pekko.testkit.PekkoSpec

class PrepareResponseSpec extends PekkoSpec {

  val parserSettings = ParserSettings.forServer(system)

  val chunkedStart = ParserOutput.ResponseStart(
    StatusCodes.OK,
    HttpProtocols.`HTTP/1.1`,
    Map.empty,
    List(),
    StreamedEntityCreator[ParserOutput, ResponseEntity] { entityChunks =>
      val chunks = entityChunks.collect {
        case EntityChunk(chunk)      => chunk
        case EntityStreamError(info) => throw EntityStreamException(info)
      }
      HttpEntity.Chunked(ContentTypes.`application/octet-stream`, chunks)
    },
    closeRequested = false)

  val strictStart = ParserOutput.ResponseStart(
    StatusCodes.OK,
    HttpProtocols.`HTTP/1.1`,
    Map.empty,
    List(),
    StrictEntityCreator(HttpEntity("body")),
    closeRequested = false)

  val chunk = ParserOutput.EntityChunk(HttpEntity.ChunkStreamPart("abc"))

  val messageEnd = ParserOutput.MessageEnd

  "The PrepareRequest stage" should {

    "not lose demand that comes in while streaming entity" in {
      val inProbe = TestPublisher.manualProbe[ParserOutput.ResponseOutput]()
      val responseProbe = TestSubscriber.manualProbe[HttpResponse]()

      Source.fromPublisher(inProbe)
        .via(new PrepareResponse(parserSettings))
        .to(Sink.fromSubscriber(responseProbe))
        .withAttributes(Attributes.inputBuffer(1, 1))
        .run()

      val inSub = inProbe.expectSubscription()
      val responseSub = responseProbe.expectSubscription()

      responseSub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(chunkedStart)
      val response = responseProbe.expectNext()

      val entityProbe = TestSubscriber.manualProbe[ByteString]()
      response.entity.dataBytes.to(Sink.fromSubscriber(entityProbe)).run()
      val entitySub = entityProbe.expectSubscription()

      entitySub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(chunk)
      entityProbe.expectNext()

      // now, before entity stream has completed
      // there is upstream demand
      responseSub.request(1)

      // then chunk completes
      entitySub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(messageEnd)
      entityProbe.expectComplete()

      // and that demand should go downstream
      // since the chunk end was consumed by the stage
      inSub.expectRequest(1)

    }

    "not lose demand that comes in while handling strict entity" in {
      val inProbe = TestPublisher.manualProbe[ParserOutput.ResponseOutput]()
      val responseProbe = TestSubscriber.manualProbe[HttpResponse]()

      Source.fromPublisher(inProbe)
        .via(new PrepareResponse(parserSettings))
        .to(Sink.fromSubscriber(responseProbe))
        .withAttributes(Attributes.inputBuffer(1, 1))
        .run()

      val inSub = inProbe.expectSubscription()
      val responseSub = responseProbe.expectSubscription()

      responseSub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(strictStart)
      responseProbe.expectNext()

      // now, before the strict message has completed
      // there is upstream demand
      responseSub.request(1)

      // then chunk completes
      inSub.expectRequest(1)
      inSub.sendNext(messageEnd)

      // and that demand should go downstream
      // since the chunk end was consumed by the stage
      inSub.expectRequest(1)

    }

    "complete entity stream then complete stage when downstream cancels" in {
      // to make it possible to cancel a big file download for example
      // without downloading the entire response first
      val inProbe = TestPublisher.manualProbe[ParserOutput.ResponseOutput]()
      val responseProbe = TestSubscriber.manualProbe[HttpResponse]()

      Source.fromPublisher(inProbe)
        .via(new PrepareResponse(parserSettings))
        .to(Sink.fromSubscriber(responseProbe))
        .withAttributes(Attributes.inputBuffer(1, 1))
        .run()

      val inSub = inProbe.expectSubscription()
      val responseSub = responseProbe.expectSubscription()

      responseSub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(chunkedStart)
      val response = responseProbe.expectNext()

      val entityProbe = TestSubscriber.manualProbe[ByteString]()
      response.entity.dataBytes.to(Sink.fromSubscriber(entityProbe)).run()
      val entitySub = entityProbe.expectSubscription()

      entitySub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(chunk)
      entityProbe.expectNext()

      // now before entity stream is completed,
      // upstream cancels
      responseSub.cancel()

      entitySub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(messageEnd)

      entityProbe.expectComplete()
      inSub.expectCancellation()
    }

    "complete stage when downstream cancels before end of strict request has arrived" in {
      val inProbe = TestPublisher.manualProbe[ParserOutput.ResponseOutput]()
      val responseProbe = TestSubscriber.manualProbe[HttpResponse]()

      Source.fromPublisher(inProbe)
        .via(new PrepareResponse(parserSettings))
        .to(Sink.fromSubscriber(responseProbe))
        .withAttributes(Attributes.inputBuffer(1, 1))
        .run()

      val inSub = inProbe.expectSubscription()
      val responseSub = responseProbe.expectSubscription()

      responseSub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(strictStart)
      responseProbe.expectNext()

      // now before end of message has arrived
      // downstream cancels
      responseSub.cancel()

      // which should cancel the stage
      inSub.expectCancellation()
    }

    "cancel entire stage when the entity stream is canceled" in {
      val inProbe = TestPublisher.manualProbe[ParserOutput.ResponseOutput]()
      val responseProbe = TestSubscriber.manualProbe[HttpResponse]()

      Source.fromPublisher(inProbe)
        .via(new PrepareResponse(parserSettings))
        .to(Sink.fromSubscriber(responseProbe))
        .withAttributes(Attributes.inputBuffer(1, 1))
        .run()

      val inSub = inProbe.expectSubscription()
      val responseSub = responseProbe.expectSubscription()

      responseSub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(chunkedStart)
      val response = responseProbe.expectNext()

      val entityProbe = TestSubscriber.manualProbe[ByteString]()
      response.entity.dataBytes.to(Sink.fromSubscriber(entityProbe)).run()
      val entitySub = entityProbe.expectSubscription()

      entitySub.request(1)
      inSub.expectRequest(1)
      inSub.sendNext(chunk)
      entityProbe.expectNext()

      // now, before entity stream has completed
      // it is cancelled
      entitySub.cancel()

      // this means that the entire stage should
      // cancel
      inSub.expectCancellation()

    }

  }

}
