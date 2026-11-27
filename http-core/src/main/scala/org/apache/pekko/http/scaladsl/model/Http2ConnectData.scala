/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.http.javadsl
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

/**
 * Incoming tunnel bytes for an application-handled regular HTTP/2 CONNECT request.
 *
 * @since 2.1.0
 */
@ApiMayChange
final class Http2ConnectData private[http] (
    val data: Source[ByteString, ?],
    token: Http2ConnectData.Acceptance)
    extends javadsl.model.Http2ConnectData {

  /**
   * Explicitly accept this CONNECT stream with status 200 and send the supplied entity as tunnel bytes.
   * Response headers can be added. Changing the final status to a non-2xx response rejects the tunnel.
   */
  def accept(outgoing: ResponseEntity): HttpResponse =
    HttpResponse(entity = outgoing).addAttribute(Http2ConnectData.acceptance, token)

  override def accept(outgoing: pekko.stream.javadsl.Source[ByteString, ?]): HttpResponse =
    accept(HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, outgoing.asScala))

  override def getData(): pekko.stream.javadsl.Source[ByteString, ?] = data.asJava
}

private[http] object Http2ConnectData {
  final class Acceptance extends RequestResponseAssociation
  val acceptance = AttributeKey[Acceptance]("internal-h2-connect-acceptance")
}
