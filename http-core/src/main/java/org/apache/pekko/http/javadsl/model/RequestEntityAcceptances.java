/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

public final class RequestEntityAcceptances {
  private RequestEntityAcceptances() {}

  public static final RequestEntityAcceptance Expected   = org.apache.pekko.http.scaladsl.model.RequestEntityAcceptance.Expected$.MODULE$;
  public static final RequestEntityAcceptance Tolerated  = org.apache.pekko.http.scaladsl.model.RequestEntityAcceptance.Tolerated$.MODULE$;
  public static final RequestEntityAcceptance Disallowed = org.apache.pekko.http.scaladsl.model.RequestEntityAcceptance.Disallowed$.MODULE$;
}
