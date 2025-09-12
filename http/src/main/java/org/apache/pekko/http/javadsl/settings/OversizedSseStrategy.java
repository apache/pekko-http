/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.javadsl.settings;

/**
 * Strategy for handling oversized SSE messages that exceed the configured max-line-size.
 * @since 1.3.0
 */
public enum OversizedSseStrategy {
  /**
   * Fail the stream with an IllegalStateException when an oversized message is encountered.
   * This is the default behavior to maintain backward compatibility.
   */
  FailStream,
  
  /**
   * Log a warning and skip the oversized message, continuing with stream processing.
   */
  LogAndSkip,
  
  /**
   * Log an info message and truncate the oversized message to the configured max-line-size,
   * continuing with stream processing.
   */
  Truncate,
  
  /**
   * Send the oversized message to dead letters, continuing with stream processing.
   */
  DeadLetter;

  /**
   * Convert this Java enum to the corresponding Scala enum value.
   */
  public org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy asScala() {
    switch (this) {
      case FailStream:
        return org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.FailStream$.MODULE$;
      case LogAndSkip:
        return org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.LogAndSkip$.MODULE$;
      case Truncate:
        return org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.Truncate$.MODULE$;
      case DeadLetter:
        return org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.DeadLetter$.MODULE$;
      default:
        throw new IllegalArgumentException("Unknown OversizedSseStrategy: " + this);
    }
  }

  /**
   * Convert from a Scala enum value to the corresponding Java enum value.
   */
  public static OversizedSseStrategy fromScala(org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy scalaStrategy) {
    if (scalaStrategy == org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.FailStream$.MODULE$) {
      return FailStream;
    } else if (scalaStrategy == org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.LogAndSkip$.MODULE$) {
      return LogAndSkip;
    } else if (scalaStrategy == org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.Truncate$.MODULE$) {
      return Truncate;
    } else if (scalaStrategy == org.apache.pekko.http.scaladsl.settings.OversizedSseStrategy.DeadLetter$.MODULE$) {
      return DeadLetter;
    } else {
      throw new IllegalArgumentException("Unknown Scala OversizedSseStrategy: " + scalaStrategy);
    }
  }
}
