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

package org.apache.pekko.http.scaladsl.unmarshalling

import org.apache.pekko
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.util.FastFuture
import pekko.util.ByteString

trait PredefinedFromEntityUnmarshallers extends MultipartUnmarshallers {

  implicit def byteStringUnmarshaller: FromEntityUnmarshaller[ByteString] =
    Unmarshaller.withMaterializer(_ =>
      implicit mat => {
        case HttpEntity.Strict(_, data) => FastFuture.successful(data)
        case entity                     => entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
      })

  implicit def byteArrayUnmarshaller: FromEntityUnmarshaller[Array[Byte]] =
    byteStringUnmarshaller.map(_.toArray[Byte])

  implicit def charArrayUnmarshaller: FromEntityUnmarshaller[Array[Char]] =
    byteStringUnmarshaller.mapWithInput { (entity, bytes) =>
      if (entity.isKnownEmpty) Array.emptyCharArray
      else {
        val charBuffer = Unmarshaller.bestUnmarshallingCharsetFor(entity).nioCharset.decode(bytes.asByteBuffer)
        val array = new Array[Char](charBuffer.length())
        charBuffer.get(array)
        array
      }
    }

  implicit def stringUnmarshaller: FromEntityUnmarshaller[String] =
    byteStringUnmarshaller.mapWithInput { (entity, bytes) =>
      if (entity.isKnownEmpty) ""
      else bytes.decodeString(Unmarshaller.bestUnmarshallingCharsetFor(entity).nioCharset)
    }

  implicit def defaultUrlEncodedFormDataUnmarshaller: FromEntityUnmarshaller[FormData] =
    urlEncodedFormDataUnmarshaller(MediaTypes.`application/x-www-form-urlencoded`)
  def urlEncodedFormDataUnmarshaller(ranges: ContentTypeRange*): FromEntityUnmarshaller[FormData] =
    stringUnmarshaller.forContentTypes(ranges: _*).mapWithInput { (entity, string) =>
      if (entity.isKnownEmpty) FormData.Empty
      else {
        try FormData(Uri.Query(string, Unmarshaller.bestUnmarshallingCharsetFor(entity).nioCharset))
        catch {
          case IllegalUriException(info) =>
            throw new IllegalArgumentException(info.formatPretty.replace("Query,", "form content,"))
        }
      }
    }
}

object PredefinedFromEntityUnmarshallers extends PredefinedFromEntityUnmarshallers
