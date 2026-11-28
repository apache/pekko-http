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

package org.apache.pekko.http.javadsl.model.headers;

import java.util.Optional;
import java.util.OptionalInt;
import org.apache.pekko.annotation.DoNotInherit;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.scaladsl.model.headers.Priority$;

/**
 * The end-to-end Priority header (RFC 9218, section 5). The options distinguish an absent hint from
 * an explicitly specified one. This header alone does not configure scheduling.
 *
 * @since 2.1.0
 */
@DoNotInherit
public abstract class Priority extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  /** Returns the explicitly specified urgency from 0 to 7, if available. */
  public abstract OptionalInt getUrgency();

  /** Returns the explicitly specified incremental preference, if available. */
  public abstract Optional<Boolean> getIncremental();

  /** Creates a Priority header with only the urgency parameter specified. */
  public static Priority create(int urgency) {
    return Priority$.MODULE$.apply(urgency);
  }

  /** Creates a Priority header with at least one explicitly specified parameter. */
  public static Priority create(OptionalInt urgency, Optional<Boolean> incremental) {
    return Priority$.MODULE$.createJava(urgency, incremental);
  }

  /**
   * Interprets all Priority fields together, in the supplied model header order. Returns an empty
   * optional if there is no readable non-empty dictionary. The header collection is unchanged, and
   * the result retains the text of unrecognized dictionary members from those model values.
   */
  public static Optional<Priority> fromHeaders(Iterable<? extends HttpHeader> headers) {
    return Priority$.MODULE$.fromJavaHeaders(headers);
  }
}
