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

public final class ContentDispositionTypes {
  private ContentDispositionTypes() {}

  public static final ContentDispositionType INLINE =
      org.apache.pekko.http.scaladsl.model.headers.ContentDispositionTypes.inline$.MODULE$;
  public static final ContentDispositionType ATTACHMENT =
      org.apache.pekko.http.scaladsl.model.headers.ContentDispositionTypes.attachment$.MODULE$;
  public static final ContentDispositionType FORM_DATA =
      org.apache.pekko.http.scaladsl.model.headers.ContentDispositionTypes.form$minusdata$.MODULE$;

  public static ContentDispositionType Ext(String name) {
    return new org.apache.pekko.http.scaladsl.model.headers.ContentDispositionTypes.Ext(name);
  }
}
