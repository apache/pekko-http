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

package org.apache.pekko.http.impl.engine.http2.hpack

import java.io.{ ByteArrayInputStream, InputStream }
import java.lang.invoke.{ MethodHandles, MethodType }

import scala.util.Try

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString
import pekko.util.ByteString.ByteString1C

/** INTERNAL API */
@InternalApi
private[http2] object ByteStringInputStream {

  private lazy val byteStringInputStreamMethodTypeOpt = Try {
    val lookup = MethodHandles.publicLookup()
    val inputStreamMethodType = MethodType.methodType(classOf[InputStream])
    lookup.findVirtual(classOf[ByteString], "asInputStream", inputStreamMethodType)
  }.toOption

  def apply(bs: ByteString): InputStream = bs match {
    case cs: ByteString1C =>
      new ByteArrayInputStream(cs.toArrayUnsafe())
    case _ => {
      byteStringInputStreamMethodTypeOpt.map { mh =>
        mh.invoke(bs).asInstanceOf[InputStream]
      }.getOrElse {
        legacyConvert(bs)
      }
    }
  }

  private def legacyConvert(bs: ByteString): InputStream = bs match {
    case cs: ByteString1C =>
      new ByteArrayInputStream(cs.toArrayUnsafe())
    case _ =>
      // NOTE: We actually measured recently, and compact + use array was pretty good usually
      legacyConvert(bs.compact)
  }
}
