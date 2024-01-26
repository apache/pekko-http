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

package org.apache.pekko.http.scaladsl.server
package directives

import java.io.File
import java.nio.file.{ Files, Paths }

import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.scaladsl.testkit.RouteTestTimeout
import pekko.testkit._

import org.scalatest.{ BeforeAndAfterAll, Inside, Inspectors }

class FileAndResourceDirectivesSymlinkSpec extends RoutingSpec
    with Inspectors with Inside with BeforeAndAfterAll {

  // need to serve from the src directory, when sbt copies the resource directory over to the
  // target directory it will resolve symlinks in the process
  val testRoot = new File("http-tests/src/test/resources")
  require(testRoot.exists(), s"testRoot was not found at ${testRoot.getAbsolutePath}")

  val tempDir = Files.createTempDirectory("pekko-http-symlink-test")
  tempDir.toFile.deleteOnExit()
  val dirWithLink = new File(tempDir.toFile, "dirWithLink")
  dirWithLink.mkdir()
  val symlink = Files.createSymbolicLink(
    Paths.get(dirWithLink.getAbsolutePath, "linked-dir"),
    new File(testRoot, "subDirectory").toPath)

  override def afterAll(): Unit = {
    super.afterAll()
    Files.deleteIfExists(symlink)
    Files.deleteIfExists(dirWithLink.toPath)
    Files.deleteIfExists(tempDir)
  }

  // operations touch files, can be randomly hit by slowness
  implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(3.seconds.dilated)

  override def testConfigSource = super.testConfigSource ++ """
    pekko.http.routing.range-coalescing-threshold = 1
  """

  "getFromDirectory" should {
    def _getFromDirectory() = getFromDirectory(dirWithLink.getCanonicalPath)

    "not follow symbolic links to find a file" in {
      EventFilter.warning(pattern = ".* points to a location that is not part of .*", occurrences = 1).intercept {
        Get("linked-dir/empty.pdf") ~> _getFromDirectory() ~> check {
          handled shouldBe false
          /* TODO: resurrect following links under an option
          responseAs[String] shouldEqual "123"
          mediaType shouldEqual `application/pdf`*/
        }
      }
    }
  }
}
