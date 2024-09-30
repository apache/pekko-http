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

import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.scaladsl.model.MediaTypes$;
import java.util.Optional;

/** Contains the set of predefined media-types. */
public final class MediaTypes {
  private MediaTypes() {}

  public static final MediaType.WithOpenCharset APPLICATION_ATOM_XML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divatom$plusxml();
  public static final MediaType.WithOpenCharset APPLICATION_BASE64 =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divbase64();
  public static final MediaType.Binary APPLICATION_CBOR =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divcbor();
  /**
   * @deprecated This format is unofficial and should not be used.
   *              Use {@link{#APPLICATION_VND_MS_EXCEL} instead.
   */
  @Deprecated
  public static final MediaType.Binary APPLICATION_EXCEL =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divexcel();
  /**
   * @deprecated This format is unofficial and should not be used.
   *              Use {@link{#FONT_WOFF} instead.
   */
  @Deprecated
  public static final MediaType.Binary APPLICATION_FONT_WOFF =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divfont$minuswoff();

  public static final MediaType.Binary APPLICATION_GNUTAR =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divgnutar();
  public static final MediaType.Binary APPLICATION_JAVA_ARCHIVE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divjava$minusarchive();
  public static final MediaType.WithOpenCharset APPLICATION_JAVASCRIPT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divjavascript();
  public static final MediaType.WithFixedCharset APPLICATION_JSON =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divjson();
  public static final MediaType.WithFixedCharset APPLICATION_JSON_PATCH_JSON =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divjson$minuspatch$plusjson();
  public static final MediaType.WithFixedCharset APPLICATION_MERGE_PATCH_JSON =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divmerge$minuspatch$plusjson();
  public static final MediaType.Binary APPLICATION_LHA =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divlha();
  public static final MediaType.Binary APPLICATION_LZX =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divlzx();
  /**
   * @deprecated This format is unofficial and should not be used.
   *              Use {@link{#APPLICATION_VND_MS_POWERPOINT} instead.
   */
  @Deprecated
  public static final MediaType.Binary APPLICATION_MSPOWERPOINT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divmspowerpoint();

  public static final MediaType.Binary APPLICATION_MSWORD =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divmsword();
  public static final MediaType.Binary APPLICATION_OCTET_STREAM =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divoctet$minusstream();
  public static final MediaType.Binary APPLICATION_GRPC_PROTO =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divgrpc$plusproto();
  public static final MediaType.Binary APPLICATION_PDF =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divpdf();
  public static final MediaType.Binary APPLICATION_POSTSCRIPT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divpostscript();
  public static final MediaType.WithOpenCharset APPLICATION_RSS_XML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divrss$plusxml();
  public static final MediaType.WithOpenCharset APPLICATION_SOAP_XML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divsoap$plusxml();
  public static final MediaType.WithFixedCharset APPLICATION_VND_API_JSON =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divvnd$u002Eapi$plusjson();
  public static final MediaType.WithOpenCharset APPLICATION_VND_GOOGLE_EARTH_KML_XML =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Egoogle$minusearth$u002Ekml$plusxml();
  public static final MediaType.Binary APPLICATION_VND_GOOGLE_EARTH_KMZ =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Egoogle$minusearth$u002Ekmz();
  public static final MediaType.Binary APPLICATION_VND_MS_EXCEL =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divvnd$u002Ems$minusexcel();
  public static final MediaType.Binary APPLICATION_VND_MS_EXCEL_ADDIN_MACROENABLED_12 =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Ems$minusexcel$u002Eaddin$u002EmacroEnabled$u002E12();
  public static final MediaType.Binary APPLICATION_VND_MS_EXCEL_SHEET_BINARY_MACROENABLED_12 =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Ems$minusexcel$u002Esheet$u002Ebinary$u002EmacroEnabled$u002E12();
  public static final MediaType.Binary APPLICATION_VND_MS_EXCEL_SHEET_MACROENABLED_12 =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Ems$minusexcel$u002Esheet$u002EmacroEnabled$u002E12();
  public static final MediaType.Binary APPLICATION_VND_MS_EXCEL_TEMPLATE_MACROENABLED_12 =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Ems$minusexcel$u002Etemplate$u002EmacroEnabled$u002E12();
  public static final MediaType.Binary APPLICATION_VND_MS_FONTOBJECT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divvnd$u002Ems$minusfontobject();
  public static final MediaType.Binary APPLICATION_VND_MS_POWERPOINT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divvnd$u002Ems$minuspowerpoint();
  public static final MediaType.Binary APPLICATION_VND_MS_POWERPOINT_ADDIN_MACROENABLED_12 =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Ems$minuspowerpoint$u002Eaddin$u002EmacroEnabled$u002E12();
  public static final MediaType.Binary APPLICATION_VND_MS_POWERPOINT_PRESENTATION_MACROENABLED_12 =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Ems$minuspowerpoint$u002Epresentation$u002EmacroEnabled$u002E12();
  public static final MediaType.Binary APPLICATION_VND_MS_POWERPOINT_SLIDESHOW_MACROENABLED_12 =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Ems$minuspowerpoint$u002Eslideshow$u002EmacroEnabled$u002E12();
  public static final MediaType.Binary APPLICATION_VND_MS_WORD_DOCUMENT_MACROENABLED_12 =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Ems$minusword$u002Edocument$u002EmacroEnabled$u002E12();
  public static final MediaType.Binary APPLICATION_VND_MS_WORD_TEMPLATE_MACROENABLED_12 =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Ems$minusword$u002Etemplate$u002EmacroEnabled$u002E12();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_CHART =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Echart();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_DATABASE =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Edatabase();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_FORMULA =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Eformula();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_GRAPHICS =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Egraphics();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_IMAGE =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Eimage();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_PRESENTATION =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Epresentation();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_SPREADSHEET =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Espreadsheet();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Etext();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_MASTER =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Etext$minusmaster();
  public static final MediaType.Binary APPLICATION_VND_OASIS_OPENDOCUMENT_TEXT_WEB =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divvnd$u002Eoasis$u002Eopendocument$u002Etext$minusweb();
  public static final MediaType.Binary
      APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_PRESENTATION =
          org.apache.pekko.http.scaladsl.model.MediaTypes
              .application$divvnd$u002Eopenxmlformats$minusofficedocument$u002Epresentationml$u002Epresentation();
  public static final MediaType.Binary
      APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDE =
          org.apache.pekko.http.scaladsl.model.MediaTypes
              .application$divvnd$u002Eopenxmlformats$minusofficedocument$u002Epresentationml$u002Eslide();
  public static final MediaType.Binary
      APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_SLIDESHOW =
          org.apache.pekko.http.scaladsl.model.MediaTypes
              .application$divvnd$u002Eopenxmlformats$minusofficedocument$u002Epresentationml$u002Eslideshow();
  public static final MediaType.Binary
      APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_PRESENTATIONML_TEMPLATE =
          org.apache.pekko.http.scaladsl.model.MediaTypes
              .application$divvnd$u002Eopenxmlformats$minusofficedocument$u002Epresentationml$u002Etemplate();
  public static final MediaType.Binary
      APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_SHEET =
          org.apache.pekko.http.scaladsl.model.MediaTypes
              .application$divvnd$u002Eopenxmlformats$minusofficedocument$u002Espreadsheetml$u002Esheet();
  public static final MediaType.Binary
      APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_SPREADSHEETML_TEMPLATE =
          org.apache.pekko.http.scaladsl.model.MediaTypes
              .application$divvnd$u002Eopenxmlformats$minusofficedocument$u002Espreadsheetml$u002Etemplate();
  public static final MediaType.Binary
      APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_DOCUMENT =
          org.apache.pekko.http.scaladsl.model.MediaTypes
              .application$divvnd$u002Eopenxmlformats$minusofficedocument$u002Ewordprocessingml$u002Edocument();
  public static final MediaType.Binary
      APPLICATION_VND_OPENXMLFORMATS_OFFICEDOCUMENT_WORDPROCESSINGML_TEMPLATE =
          org.apache.pekko.http.scaladsl.model.MediaTypes
              .application$divvnd$u002Eopenxmlformats$minusofficedocument$u002Ewordprocessingml$u002Etemplate();
  public static final MediaType.Binary APPLICATION_X_7Z_COMPRESSED =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minus7z$minuscompressed();
  public static final MediaType.Binary APPLICATION_X_ACE_COMPRESSED =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusace$minuscompressed();
  public static final MediaType.Binary APPLICATION_X_APPLE_DISKIMAGE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusapple$minusdiskimage();
  public static final MediaType.Binary APPLICATION_X_ARC_COMPRESSED =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusarc$minuscompressed();
  public static final MediaType.Binary APPLICATION_X_BZIP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusbzip();
  public static final MediaType.Binary APPLICATION_X_BZIP2 =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusbzip2();
  public static final MediaType.Binary APPLICATION_X_CHROME_EXTENSION =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minuschrome$minusextension();
  public static final MediaType.Binary APPLICATION_X_COMPRESS =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minuscompress();
  public static final MediaType.Binary APPLICATION_X_COMPRESSED =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minuscompressed();
  public static final MediaType.Binary APPLICATION_X_DEBIAN_PACKAGE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusdebian$minuspackage();
  public static final MediaType.Binary APPLICATION_X_DVI =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusdvi();
  public static final MediaType.Binary APPLICATION_X_FONT_TRUETYPE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusfont$minustruetype();
  public static final MediaType.Binary APPLICATION_X_FONT_OPENTYPE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusfont$minusopentype();
  public static final MediaType.Binary APPLICATION_X_GTAR =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusgtar();
  public static final MediaType.Binary APPLICATION_X_GZIP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusgzip();
  public static final MediaType.WithOpenCharset APPLICATION_X_LATEX =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minuslatex();
  public static final MediaType.Binary APPLICATION_X_RAR_COMPRESSED =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusrar$minuscompressed();
  public static final MediaType.Binary APPLICATION_X_REDHAT_PACKAGE_MANAGER =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divx$minusredhat$minuspackage$minusmanager();
  public static final MediaType.Binary APPLICATION_X_SHOCKWAVE_FLASH =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusshockwave$minusflash();
  public static final MediaType.Binary APPLICATION_X_TAR =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minustar();
  public static final MediaType.Binary APPLICATION_X_TEX =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minustex();
  public static final MediaType.Binary APPLICATION_X_TEXINFO =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minustexinfo();
  public static final MediaType.WithOpenCharset APPLICATION_X_VRML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusvrml();
  public static final MediaType.WithFixedCharset APPLICATION_X_WWW_FORM_URLENCODED =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divx$minuswww$minusform$minusurlencoded();
  public static final MediaType.Binary APPLICATION_X_X509_CA_CERT =
      org.apache.pekko.http.scaladsl.model.MediaTypes
          .application$divx$minusx509$minusca$minuscert();
  public static final MediaType.Binary APPLICATION_X_XPINSTALL =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divx$minusxpinstall();
  public static final MediaType.WithOpenCharset APPLICATION_XHTML_XML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divxhtml$plusxml();
  public static final MediaType.WithOpenCharset APPLICATION_XML_DTD =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divxml$minusdtd();
  public static final MediaType.WithOpenCharset APPLICATION_XML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divxml();
  public static final MediaType.Binary APPLICATION_ZIP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.application$divzip();

  public static final MediaType.Binary AUDIO_AIFF =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divaiff();
  public static final MediaType.Binary AUDIO_BASIC =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divbasic();
  public static final MediaType.Binary AUDIO_MIDI =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divmidi();
  public static final MediaType.Binary AUDIO_MOD =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divmod();
  public static final MediaType.Binary AUDIO_MPEG =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divmpeg();
  public static final MediaType.Binary AUDIO_OGG =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divogg();
  public static final MediaType.Binary AUDIO_VOC =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divvoc();
  public static final MediaType.Binary AUDIO_VORBIS =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divvorbis();
  public static final MediaType.Binary AUDIO_VOXWARE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divvoxware();
  public static final MediaType.Binary AUDIO_WAV =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divwav();
  public static final MediaType.Binary AUDIO_X_REALAUDIO =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divx$minusrealaudio();
  public static final MediaType.Binary AUDIO_X_PSID =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divx$minuspsid();
  public static final MediaType.Binary AUDIO_XM =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divxm();
  public static final MediaType.Binary AUDIO_WEBM =
      org.apache.pekko.http.scaladsl.model.MediaTypes.audio$divwebm();

  public static final MediaType.Binary FONT_WOFF =
      org.apache.pekko.http.scaladsl.model.MediaTypes.font$divwoff();
  public static final MediaType.Binary FONT_WOFF_2 =
      org.apache.pekko.http.scaladsl.model.MediaTypes.font$divwoff2();

  public static final MediaType.Binary IMAGE_GIF =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divgif();
  public static final MediaType.Binary IMAGE_JPEG =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divjpeg();
  public static final MediaType.Binary IMAGE_PICT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divpict();
  public static final MediaType.Binary IMAGE_PNG =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divpng();
  public static final MediaType.Binary IMAGE_SVGZ =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divsvgz();
  public static final MediaType.Binary IMAGE_SVG_XML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divsvg$plusxml();
  public static final MediaType.Binary IMAGE_TIFF =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divtiff();
  public static final MediaType.Binary IMAGE_X_ICON =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divx$minusicon();
  public static final MediaType.Binary IMAGE_X_MS_BMP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divx$minusms$minusbmp();
  public static final MediaType.Binary IMAGE_X_PCX =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divx$minuspcx();
  public static final MediaType.Binary IMAGE_X_PICT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divx$minuspict();
  public static final MediaType.Binary IMAGE_X_QUICKTIME =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divx$minusquicktime();
  public static final MediaType.Binary IMAGE_X_RGB =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divx$minusrgb();
  public static final MediaType.Binary IMAGE_X_XBITMAP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divx$minusxbitmap();
  public static final MediaType.Binary IMAGE_X_XPIXMAP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divx$minusxpixmap();
  public static final MediaType.Binary IMAGE_WEBP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.image$divwebp();

  public static final MediaType.Binary MESSAGE_HTTP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.message$divhttp();
  public static final MediaType.Binary MESSAGE_DELIVERY_STATUS =
      org.apache.pekko.http.scaladsl.model.MediaTypes.message$divdelivery$minusstatus();
  public static final MediaType.Binary MESSAGE_RFC822 =
      org.apache.pekko.http.scaladsl.model.MediaTypes.message$divrfc822();

  public static final MediaType.Binary MULTIPART_MIXED =
      org.apache.pekko.http.scaladsl.model.MediaTypes.multipart$divmixed();
  public static final MediaType.Binary MULTIPART_ALTERNATIVE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.multipart$divalternative();
  public static final MediaType.Binary MULTIPART_RELATED =
      org.apache.pekko.http.scaladsl.model.MediaTypes.multipart$divrelated();
  public static final MediaType.Binary MULTIPART_FORM_DATA =
      org.apache.pekko.http.scaladsl.model.MediaTypes.multipart$divform$minusdata();
  public static final MediaType.Binary MULTIPART_SIGNED =
      org.apache.pekko.http.scaladsl.model.MediaTypes.multipart$divsigned();
  public static final MediaType.Binary MULTIPART_ENCRYPTED =
      org.apache.pekko.http.scaladsl.model.MediaTypes.multipart$divencrypted();
  public static final MediaType.Binary MULTIPART_BYTERANGES =
      org.apache.pekko.http.scaladsl.model.MediaTypes.multipart$divbyteranges();

  public static final MediaType.WithOpenCharset TEXT_ASP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divasp();
  public static final MediaType.WithOpenCharset TEXT_CACHE_MANIFEST =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divcache$minusmanifest();
  public static final MediaType.WithOpenCharset TEXT_CALENDAR =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divcalendar();
  public static final MediaType.WithOpenCharset TEXT_CSS =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divcss();
  public static final MediaType.WithOpenCharset TEXT_CSV =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divcsv();
  public static final MediaType.WithFixedCharset TEXT_EVENT_STREAM =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divevent$minusstream();
  public static final MediaType.WithOpenCharset TEXT_HTML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divhtml();
  public static final MediaType.WithOpenCharset TEXT_MARKDOWN =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divmarkdown();
  public static final MediaType.WithOpenCharset TEXT_MCF =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divmcf();
  public static final MediaType.WithOpenCharset TEXT_PLAIN =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divplain();
  public static final MediaType.WithOpenCharset TEXT_RICHTEXT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divrichtext();
  public static final MediaType.WithOpenCharset TEXT_TAB_SEPARATED_VALUES =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divtab$minusseparated$minusvalues();
  public static final MediaType.WithOpenCharset TEXT_URI_LIST =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divuri$minuslist();
  public static final MediaType.WithOpenCharset TEXT_VND_WAP_WML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divvnd$u002Ewap$u002Ewml();
  public static final MediaType.WithOpenCharset TEXT_VND_WAP_WMLSCRIPT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divvnd$u002Ewap$u002Ewmlscript();
  public static final MediaType.WithOpenCharset TEXT_X_ASM =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusasm();
  public static final MediaType.WithOpenCharset TEXT_X_C =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusc();
  public static final MediaType.WithOpenCharset TEXT_X_COMPONENT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minuscomponent();
  public static final MediaType.WithOpenCharset TEXT_X_H =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minush();
  public static final MediaType.WithOpenCharset TEXT_X_JAVA_SOURCE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusjava$minussource();
  public static final MediaType.WithOpenCharset TEXT_X_PASCAL =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minuspascal();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscript();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTCSH =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptcsh();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTELISP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptelisp();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTKSH =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptksh();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTLISP =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptlisp();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTPERL =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptperl();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTPERL_MODULE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptperl$minusmodule();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTPHYTON =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptphyton();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTREXX =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptrexx();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTSCHEME =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptscheme();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTSH =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptsh();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTTCL =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscripttcl();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTTCSH =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscripttcsh();
  public static final MediaType.WithOpenCharset TEXT_X_SCRIPTZSH =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusscriptzsh();
  public static final MediaType.WithOpenCharset TEXT_X_SERVER_PARSED_HTML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusserver$minusparsed$minushtml();
  public static final MediaType.WithOpenCharset TEXT_X_SETEXT =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minussetext();
  public static final MediaType.WithOpenCharset TEXT_X_SGML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minussgml();
  public static final MediaType.WithOpenCharset TEXT_X_SPEECH =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusspeech();
  public static final MediaType.WithOpenCharset TEXT_X_UUENCODE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusuuencode();
  public static final MediaType.WithOpenCharset TEXT_X_VCALENDAR =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusvcalendar();
  public static final MediaType.WithOpenCharset TEXT_X_VCARD =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divx$minusvcard();
  public static final MediaType.WithOpenCharset TEXT_XML =
      org.apache.pekko.http.scaladsl.model.MediaTypes.text$divxml();

  public static final MediaType.Binary VIDEO_AVS_VIDEO =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divavs$minusvideo();
  public static final MediaType.Binary VIDEO_DIVX =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divdivx();
  public static final MediaType.Binary VIDEO_GL =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divgl();
  public static final MediaType.Binary VIDEO_MP4 =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divmp4();
  public static final MediaType.Binary VIDEO_MPEG =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divmpeg();
  public static final MediaType.Binary VIDEO_OGG =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divogg();
  public static final MediaType.Binary VIDEO_QUICKTIME =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divquicktime();
  public static final MediaType.Binary VIDEO_X_DV =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divx$minusdv();
  public static final MediaType.Binary VIDEO_X_FLV =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divx$minusflv();
  public static final MediaType.Binary VIDEO_X_MOTION_JPEG =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divx$minusmotion$minusjpeg();
  public static final MediaType.Binary VIDEO_X_MS_ASF =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divx$minusms$minusasf();
  public static final MediaType.Binary VIDEO_X_MSVIDEO =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divx$minusmsvideo();
  public static final MediaType.Binary VIDEO_X_SGI_MOVIE =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divx$minussgi$minusmovie();
  public static final MediaType.Binary VIDEO_WEBM =
      org.apache.pekko.http.scaladsl.model.MediaTypes.video$divwebm();

  public static final MediaType.Compressibility COMPRESSIBLE =
      org.apache.pekko.http.scaladsl.model.MediaType.Compressible$.MODULE$;
  public static final MediaType.Compressibility NOT_COMPRESSIBLE =
      org.apache.pekko.http.scaladsl.model.MediaType.NotCompressible$.MODULE$;
  public static final MediaType.Compressibility GZIPPED =
      org.apache.pekko.http.scaladsl.model.MediaType.Gzipped$.MODULE$;

  public static MediaType.Binary applicationBinary(
      String subType, boolean compressible, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        compressible
            ? org.apache.pekko.http.scaladsl.model.MediaType.Compressible$.MODULE$
            : org.apache.pekko.http.scaladsl.model.MediaType.NotCompressible$.MODULE$;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.applicationBinary(subType, comp, fileEx);
  }

  public static MediaType.Binary applicationBinary(
      String subType, MediaType.Compressibility compressibility, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        (org.apache.pekko.http.scaladsl.model.MediaType.Compressibility) compressibility;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.applicationBinary(subType, comp, fileEx);
  }

  public static MediaType.WithFixedCharset applicationWithFixedCharset(
      String subType, HttpCharset charset, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.HttpCharset cs =
        (org.apache.pekko.http.scaladsl.model.HttpCharset) charset;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.applicationWithFixedCharset(
        subType, cs, fileEx);
  }

  public static MediaType.WithOpenCharset applicationWithOpenCharset(
      String subType, String... fileExtensions) {
    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.applicationWithOpenCharset(
        subType, fileEx);
  }

  public static MediaType.Binary audio(
      String subType, boolean compressible, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        compressible
            ? org.apache.pekko.http.scaladsl.model.MediaType.Compressible$.MODULE$
            : org.apache.pekko.http.scaladsl.model.MediaType.NotCompressible$.MODULE$;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.audio(subType, comp, fileEx);
  }

  public static MediaType.Binary audio(
      String subType, MediaType.Compressibility compressibility, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        (org.apache.pekko.http.scaladsl.model.MediaType.Compressibility) compressibility;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.audio(subType, comp, fileEx);
  }

  public static MediaType.Binary image(
      String subType, boolean compressible, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        compressible
            ? org.apache.pekko.http.scaladsl.model.MediaType.Compressible$.MODULE$
            : org.apache.pekko.http.scaladsl.model.MediaType.NotCompressible$.MODULE$;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.image(subType, comp, fileEx);
  }

  public static MediaType.Binary image(
      String subType, MediaType.Compressibility compressibility, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        (org.apache.pekko.http.scaladsl.model.MediaType.Compressibility) compressibility;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.image(subType, comp, fileEx);
  }

  public static MediaType.Binary message(
      String subType, boolean compressible, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        compressible
            ? org.apache.pekko.http.scaladsl.model.MediaType.Compressible$.MODULE$
            : org.apache.pekko.http.scaladsl.model.MediaType.NotCompressible$.MODULE$;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.message(subType, comp, fileEx);
  }

  public static MediaType.Binary message(
      String subType, MediaType.Compressibility compressibility, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        (org.apache.pekko.http.scaladsl.model.MediaType.Compressibility) compressibility;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.message(subType, comp, fileEx);
  }

  public static MediaType.WithOpenCharset text(String subType, String... fileExtensions) {
    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.text(subType, fileEx);
  }

  public static MediaType.Binary video(
      String subType, boolean compressible, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        compressible
            ? org.apache.pekko.http.scaladsl.model.MediaType.Compressible$.MODULE$
            : org.apache.pekko.http.scaladsl.model.MediaType.NotCompressible$.MODULE$;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.video(subType, comp, fileEx);
  }

  public static MediaType.Binary video(
      String subType, MediaType.Compressibility compressibility, String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        (org.apache.pekko.http.scaladsl.model.MediaType.Compressibility) compressibility;

    scala.collection.immutable.Seq<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions));

    return org.apache.pekko.http.scaladsl.model.MediaType.video(subType, comp, fileEx);
  }

  public static MediaType.Binary customBinary(
      String mainType, String subType, boolean compressible) {
    return customBinary(
        mainType, subType, compressible, java.util.Collections.<String, String>emptyMap(), false);
  }

  public static MediaType.Binary customBinary(
      String mainType, String subType, MediaType.Compressibility compressibility) {
    return customBinary(
        mainType,
        subType,
        compressibility,
        java.util.Collections.<String, String>emptyMap(),
        false);
  }

  // arguments have been reordered due to varargs having to be the last argument
  // should we create multiple overloads of this function?
  public static MediaType.Binary customBinary(
      String mainType,
      String subType,
      boolean compressible,
      java.util.Map<String, String> params,
      boolean allowArbitrarySubtypes,
      String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        compressible
            ? org.apache.pekko.http.scaladsl.model.MediaType.Compressible$.MODULE$
            : org.apache.pekko.http.scaladsl.model.MediaType.NotCompressible$.MODULE$;

    scala.collection.immutable.List<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions))
            .toList();

    scala.collection.immutable.Map<String, String> p = Util.convertMapToScala(params);

    return org.apache.pekko.http.scaladsl.model.MediaType.customBinary(
        mainType, subType, comp, fileEx, p, allowArbitrarySubtypes);
  }

  public static MediaType.Binary customBinary(
      String mainType,
      String subType,
      MediaType.Compressibility compressibility,
      java.util.Map<String, String> params,
      boolean allowArbitrarySubtypes,
      String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        (org.apache.pekko.http.scaladsl.model.MediaType.Compressibility) compressibility;

    scala.collection.immutable.List<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions))
            .toList();

    scala.collection.immutable.Map<String, String> p = Util.convertMapToScala(params);

    return org.apache.pekko.http.scaladsl.model.MediaType.customBinary(
        mainType, subType, comp, fileEx, p, allowArbitrarySubtypes);
  }

  public static MediaType.WithFixedCharset customWithFixedCharset(
      String mainType,
      String subType,
      HttpCharset charset,
      java.util.Map<String, String> params,
      boolean allowArbitrarySubtypes,
      String... fileExtensions) {
    org.apache.pekko.http.scaladsl.model.HttpCharset cs =
        (org.apache.pekko.http.scaladsl.model.HttpCharset) charset;

    scala.collection.immutable.List<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions))
            .toList();

    scala.collection.immutable.Map<String, String> p = Util.convertMapToScala(params);

    return org.apache.pekko.http.scaladsl.model.MediaType.customWithFixedCharset(
        mainType, subType, cs, fileEx, p, allowArbitrarySubtypes);
  }

  public static MediaType.WithOpenCharset customWithOpenCharset(
      String mainType,
      String subType,
      java.util.Map<String, String> params,
      boolean allowArbitrarySubtypes,
      String... fileExtensions) {
    scala.collection.immutable.List<String> fileEx =
        Util.<String, String>convertIterable(java.util.Arrays.asList(fileExtensions))
            .toList();

    scala.collection.immutable.Map<String, String> p = Util.convertMapToScala(params);

    return org.apache.pekko.http.scaladsl.model.MediaType.customWithOpenCharset(
        mainType, subType, fileEx, p, allowArbitrarySubtypes);
  }

  public static MediaType.Multipart customMultipart(
      String subType, java.util.Map<String, String> params) {
    scala.collection.immutable.Map<String, String> p = Util.convertMapToScala(params);
    return org.apache.pekko.http.scaladsl.model.MediaType.customMultipart(subType, p);
  }

  /** Creates a custom media type. */
  public static MediaType custom(String value, boolean binary, boolean compressible) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        compressible
            ? org.apache.pekko.http.scaladsl.model.MediaType.Compressible$.MODULE$
            : org.apache.pekko.http.scaladsl.model.MediaType.NotCompressible$.MODULE$;

    return org.apache.pekko.http.scaladsl.model.MediaType.custom(
        value, binary, comp, org.apache.pekko.http.scaladsl.model.MediaType.custom$default$4());
  }

  public static MediaType custom(
      String value, boolean binary, MediaType.Compressibility compressibility) {
    org.apache.pekko.http.scaladsl.model.MediaType.Compressibility comp =
        (org.apache.pekko.http.scaladsl.model.MediaType.Compressibility) compressibility;

    return org.apache.pekko.http.scaladsl.model.MediaType.custom(
        value, binary, comp, org.apache.pekko.http.scaladsl.model.MediaType.custom$default$4());
  }

  /** Looks up a media-type with the given main-type and sub-type. */
  public static Optional<MediaType> lookup(String mainType, String subType) {
    return Util
        .<scala.Tuple2<String, String>, MediaType, org.apache.pekko.http.scaladsl.model.MediaType>
            lookupInRegistry(
                MediaTypes$.MODULE$, new scala.Tuple2<String, String>(mainType, subType));
  }
}
