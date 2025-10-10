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

package docs.http.scaladsl.server

import java.io.File

import org.apache.pekko
import pekko.Done
import pekko.actor.ActorRef
import pekko.http.scaladsl.model.Multipart.FormData.BodyPart
import pekko.stream.scaladsl.Framing
import pekko.stream.scaladsl._
import pekko.http.scaladsl.model.Multipart
import pekko.http.scaladsl.server.RoutingSpec
import pekko.util.ByteString
import docs.CompileOnlySpec

import scala.concurrent.duration._
import scala.concurrent.Future

class FileUploadExamplesSpec extends RoutingSpec with CompileOnlySpec {

  case class Video(file: File, title: String, author: String)
  object db {
    def create(video: Video): Future[Unit] = Future.successful(())
  }

  "simple-upload" in {
    // #simple-upload
    val uploadVideo =
      path("video") {
        entity(as[Multipart.FormData]) { formData =>
          // collect all parts of the multipart as it arrives into a map
          val allPartsF: Future[Map[String, Any]] = formData.parts.mapAsync[(String, Any)](1) {

            case b: BodyPart if b.name == "file" =>
              // stream into a file as the chunks of it arrives and return a future
              // file to where it got stored
              val file = File.createTempFile("upload", "tmp")
              b.entity.dataBytes.runWith(FileIO.toPath(file.toPath)).map(_ =>
                b.name -> file)

            case b: BodyPart =>
              // collect form field values
              b.toStrict(2.seconds).map(strict =>
                b.name -> strict.entity.data.utf8String)

          }.runFold(Map.empty[String, Any])((map, tuple) => map + tuple)

          val done = allPartsF.map { allParts =>
            // You would have some better validation/unmarshalling here
            db.create(Video(
              file = allParts("file").asInstanceOf[File],
              title = allParts("title").asInstanceOf[String],
              author = allParts("author").asInstanceOf[String]))
          }

          // when processing have finished create a response for the user
          onSuccess(allPartsF) { allParts =>
            complete {
              "ok!"
            }
          }
        }
      }
    // #simple-upload
  }

  object MetadataActor {
    case class Entry(id: Long, values: Seq[String])
  }
  val metadataActor: ActorRef = system.deadLetters

  "stream-csv-upload" in {
    // #stream-csv-upload
    val splitLines = Framing.delimiter(ByteString("\n"), 256)

    val csvUploads =
      path("metadata" / LongNumber) { id =>
        entity(as[Multipart.FormData]) { formData =>
          val done: Future[Done] = formData.parts.mapAsync(1) {
            case b: BodyPart if b.filename.exists(_.endsWith(".csv")) =>
              b.entity.dataBytes
                .via(splitLines)
                .map(_.utf8String.split(",").toVector)
                .runForeach(csv =>
                  metadataActor ! MetadataActor.Entry(id, csv))
            case _ => Future.successful(Done)
          }.runWith(Sink.ignore)

          // when processing have finished create a response for the user
          onSuccess(done) { _ =>
            complete {
              "ok!"
            }
          }
        }
      }
    // #stream-csv-upload
  }

}
