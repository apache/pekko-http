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

import java.util.Optional;

public abstract class LinkParam {
  public abstract String key();

  public abstract Object value();

  /**
   * The Java view of an extension attribute on a Link header.
   *
   * @since 2.1.0
   */
  public interface Extension {
    /** Returns an empty Optional when the extension was supplied without a value. */
    Optional<String> getValueOption();
  }
}
