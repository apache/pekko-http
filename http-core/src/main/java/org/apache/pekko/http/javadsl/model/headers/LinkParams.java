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

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.MediaType;
import org.apache.pekko.http.javadsl.model.Uri;
import org.apache.pekko.http.impl.util.Util;

public final class LinkParams {
  private LinkParams() {}

  public static final LinkParam next =
      org.apache.pekko.http.scaladsl.model.headers.LinkParams.next();
  public static final LinkParam prev =
      org.apache.pekko.http.scaladsl.model.headers.LinkParams.prev();
  public static final LinkParam first =
      org.apache.pekko.http.scaladsl.model.headers.LinkParams.first();
  public static final LinkParam last =
      org.apache.pekko.http.scaladsl.model.headers.LinkParams.last();

  public static LinkParam rel(String value) {
    return new org.apache.pekko.http.scaladsl.model.headers.LinkParams.rel(value);
  }

  public static LinkParam anchor(Uri uri) {
    return new org.apache.pekko.http.scaladsl.model.headers.LinkParams.anchor(uri.asScala());
  }

  public static LinkParam rev(String value) {
    return new org.apache.pekko.http.scaladsl.model.headers.LinkParams.rev(value);
  }

  public static LinkParam hreflang(Language language) {
    return new org.apache.pekko.http.scaladsl.model.headers.LinkParams.hreflang(
        (org.apache.pekko.http.scaladsl.model.headers.Language) language);
  }

  public static LinkParam media(String desc) {
    return new org.apache.pekko.http.scaladsl.model.headers.LinkParams.media(desc);
  }

  public static LinkParam title(String title) {
    return new org.apache.pekko.http.scaladsl.model.headers.LinkParams.title(title);
  }

  public static LinkParam title_All(String title) {
    return new org.apache.pekko.http.scaladsl.model.headers.LinkParams.title$times(title);
  }

  public static LinkParam type(MediaType type) {
    return new org.apache.pekko.http.scaladsl.model.headers.LinkParams.type(
        (org.apache.pekko.http.scaladsl.model.MediaType) type);
  }
}
