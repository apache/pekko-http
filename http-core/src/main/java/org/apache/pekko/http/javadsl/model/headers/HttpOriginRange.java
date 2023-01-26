/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.scaladsl.model.headers.HttpOriginRange$;
import org.apache.pekko.http.impl.util.Util;

/**
 * @see HttpOriginRanges for convenience access to often used values.
 */
public abstract class HttpOriginRange {
  public abstract boolean matches(HttpOrigin origin);

  public static HttpOriginRange create(HttpOrigin... origins) {
    return HttpOriginRange$.MODULE$.apply(Util.<HttpOrigin, org.apache.pekko.http.scaladsl.model.headers.HttpOrigin>convertArray(origins));
  }
}
