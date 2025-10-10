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

import org.apache.pekko.testkit.PekkoSpec

class MarshalSpec extends PekkoSpec {

  "use marshal" in {
    // #use-marshal
    import scala.concurrent.Await
    import scala.concurrent.duration._

    import org.apache.pekko
    import pekko.http.scaladsl.marshalling.Marshal
    import pekko.http.scaladsl.model._

    import system.dispatcher // ExecutionContext

    val string = "Yeah"
    val entityFuture = Marshal(string).to[MessageEntity]
    val entity = Await.result(entityFuture, 1.second) // don't block in non-test code!
    entity.contentType shouldEqual ContentTypes.`text/plain(UTF-8)`

    val errorMsg = "Easy, pal!"
    val responseFuture = Marshal(420 -> errorMsg).to[HttpResponse]
    val response = Await.result(responseFuture, 1.second) // don't block in non-test code!
    response.status shouldEqual StatusCodes.EnhanceYourCalm
    response.entity.contentType shouldEqual ContentTypes.`text/plain(UTF-8)`

    val request = HttpRequest(headers = List(headers.Accept(MediaTypes.`application/json`)))
    val responseText = "Plaintext"
    val respFuture = Marshal(responseText).toResponseFor(request) // with content negotiation!
    a[Marshal.UnacceptableResponseContentTypeException] should be thrownBy {
      Await.result(respFuture, 1.second) // client requested JSON, we only have text/plain!
    }
    // #use-marshal
  }

}
