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

package org.apache.pekko.http.javadsl.model;

public final class TransferEncodings {
  private TransferEncodings() {}

  public static final TransferEncoding CHUNKED =
      org.apache.pekko.http.scaladsl.model.TransferEncodings.chunked$.MODULE$;
  public static final TransferEncoding COMPRESS =
      org.apache.pekko.http.scaladsl.model.TransferEncodings.compress$.MODULE$;
  public static final TransferEncoding DEFLATE =
      org.apache.pekko.http.scaladsl.model.TransferEncodings.deflate$.MODULE$;
  public static final TransferEncoding GZIP =
      org.apache.pekko.http.scaladsl.model.TransferEncodings.gzip$.MODULE$;
  public static final TransferEncoding TRAILERS =
      org.apache.pekko.http.scaladsl.model.TransferEncodings.trailers$.MODULE$;
}
