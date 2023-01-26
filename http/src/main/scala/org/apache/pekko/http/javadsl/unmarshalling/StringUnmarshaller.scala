/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.unmarshalling

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.annotation.InternalApi

object StringUnmarshaller {

  /**
   * Turns the given asynchronous function into an unmarshaller from String to B.
   */
  def async[B](f: java.util.function.Function[String, CompletionStage[B]]): Unmarshaller[String, B] =
    Unmarshaller.async(f)

  /**
   * Turns the given function into an unmarshaller from String to B.
   */
  def sync[B](f: java.util.function.Function[String, B]): Unmarshaller[String, B] = Unmarshaller.sync(f)
}

/**
 * INTERNAL API
 */
@InternalApi
private[unmarshalling] object StringUnmarshallerPredef
    extends pekko.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers {}
