/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

object GitHub {

  def envTokenOrThrow: String =
    System.getenv("PR_VALIDATOR_GH_TOKEN")
      .ensuring(_ != null, "No PR_VALIDATOR_GH_TOKEN env var provided, unable to reach github!")

  def url(v: String, isSnapshot: Boolean): String = {
    val branch = if (isSnapshot) "main" else "v" + v
    "https://github.com/apache/pekko-http/tree/" + branch
  }
}
