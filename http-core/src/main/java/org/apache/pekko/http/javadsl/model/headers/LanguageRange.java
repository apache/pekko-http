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

public interface LanguageRange {
  public abstract String primaryTag();

  public abstract float qValue();

  public abstract boolean matches(Language language);

  public abstract Iterable<String> getSubTags();

  public abstract LanguageRange withQValue(float qValue);
}
