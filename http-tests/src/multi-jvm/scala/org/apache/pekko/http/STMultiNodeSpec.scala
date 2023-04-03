/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http

import org.apache.pekko
import pekko.http.scaladsl.Http.ServerBinding
import pekko.remote.testkit.MultiNodeSpecCallbacks
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers

trait STMultiNodeSpec extends MultiNodeSpecCallbacks with AnyWordSpecLike with Matchers with BeforeAndAfterAll
    with ScalaFutures {

  def binding: Option[ServerBinding]

  override def beforeAll() =
    multiNodeSpecBeforeAll()

  override def afterAll() = {
    binding.foreach { _.unbind().futureValue }
    multiNodeSpecAfterAll()
  }

}
