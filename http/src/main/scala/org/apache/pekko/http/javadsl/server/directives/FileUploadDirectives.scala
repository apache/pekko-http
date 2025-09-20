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

package org.apache.pekko.http.javadsl.server.directives

import java.io.File
import java.util.{ List => JList, Map => JMap }
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.function.{ BiFunction, Function => JFunction }

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.http.javadsl.model.ContentType
import pekko.http.javadsl.server.Route
import pekko.http.scaladsl.server.{ Directives => D }
import pekko.japi.Util
import pekko.stream.javadsl.Source
import pekko.util.ByteString

abstract class FileUploadDirectives extends FileAndResourceDirectives {

  /**
   * Streams the bytes of the file submitted using multipart with the given file name into a designated file on disk.
   * If there is an error writing to disk the request will be failed with the thrown exception, if there is no such
   * field the request will be rejected, if there are multiple file parts with the same name, the first one will be
   * used and the subsequent ones ignored.
   */
  @ApiMayChange
  def storeUploadedFile(
      fieldName: String, destFn: JFunction[FileInfo, File], inner: BiFunction[FileInfo, File, Route]): Route =
    RouteAdapter {
      D.storeUploadedFile(fieldName, destFn.apply) { case (info, file) => inner.apply(info, file).delegate }
    }

  /**
   * Streams the bytes of the file submitted using multipart with the given field name into designated files on disk.
   * If there is an error writing to disk the request will be failed with the thrown exception, if there is no such
   * field the request will be rejected. Stored files are cleaned up on exit but not on failure.
   */
  @ApiMayChange
  def storeUploadedFiles(fieldName: String, destFn: JFunction[FileInfo, File],
      inner: JFunction[JList[JMap.Entry[FileInfo, File]], Route]): Route = RouteAdapter {
    D.storeUploadedFiles(fieldName, destFn.apply) { files =>
      val entries = files.map { case (info, src) => new SimpleImmutableEntry(fileInfoToJava(info), src) }
      inner.apply(Util.javaArrayList(entries)).delegate
    }
  }

  /**
   * Collects each body part that is a multipart file as a tuple containing metadata and a `Source`
   * for streaming the file contents somewhere. If there is no such field the request will be rejected,
   * if there are multiple file parts with the same name, the first one will be used and the subsequent
   * ones ignored.
   */
  def fileUpload(fieldName: String, inner: BiFunction[FileInfo, Source[ByteString, Any], Route]): Route = RouteAdapter {
    D.fileUpload(fieldName) { case (info, src) => inner.apply(info, src.asJava).delegate }
  }

  /**
   * Collects each body part that is a multipart file as a tuple containing metadata and a `Source`
   * for streaming the file contents somewhere. If there is no such field the request will be rejected.
   * Files are buffered into temporary files on disk so in-memory buffers don't overflow. The temporary
   * files are cleaned up once materialized, or on exit if the stream is not consumed.
   */
  @ApiMayChange
  def fileUploadAll(
      fieldName: String, inner: JFunction[JList[JMap.Entry[FileInfo, Source[ByteString, Any]]], Route]): Route =
    RouteAdapter {
      D.fileUploadAll(fieldName) { files =>
        val entries = files.map { case (info, src) => new SimpleImmutableEntry(fileInfoToJava(info), src.asJava) }
        inner.apply(Util.javaArrayList(entries)).delegate
      }
    }

  // Ensure invariant instance of FileInfo
  private def fileInfoToJava[F <: FileInfo](f: F): FileInfo = f
}

/**
 * Additional metadata about the file being uploaded/that was uploaded using the [[FileUploadDirectives]]
 */
abstract class FileInfo {

  /**
   * Name of the form field the file was uploaded in
   */
  def getFieldName: String

  /**
   * User specified name of the uploaded file
   */
  def getFileName: String

  /**
   * Content type of the file
   */
  def getContentType: ContentType
}
