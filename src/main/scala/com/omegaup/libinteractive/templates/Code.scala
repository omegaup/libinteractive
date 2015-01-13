// Copyright (c) 2015 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.templates

import scala.collection.immutable

import play.twirl.api.BufferedContent
import play.twirl.api.Format

// This is a workaround for Twirl's poor handling of whitespace. Every time a
// Scala block appears, Twirl preserves the whitespace around it, introducing a
// lot of unnecessary blank lines.
class StringAppender {
  private val builder = new StringBuilder
  var chopNext = false
  def append(string: String): Unit = {
    if (string == null || string.length == 0) return

    if (chopNext && string.charAt(0) == '\n') {
      builder.append(string.substring(1))
    } else {
      builder.append(string)
    }
    // Chop the leading newline if this node ends in newline.
    chopNext = string.charAt(string.length - 1) == '\n'
  }
  override def toString() = builder.toString
}

// This absolutely cannot be a class hierarchy: BaseScalaTemplate only matches
// against this class and not against this class and its descendents, which
// would introduce unnecessary extra escaping.
class Code(val text: String, val elements: immutable.Seq[Code])
    extends play.twirl.api.Appendable[Code] {
  def this(text: String) = this(text, Nil)
  def this(elements: immutable.Seq[Code]) = this(null, elements)

  val contentType = "text/plain"

  def visit(appender: StringAppender): Unit = {
    if (!elements.isEmpty) {
      // First node in a Seq gets its leading newline chopped.
      appender.chopNext = true
      elements.foreach(_.visit(appender))
      // First node following a Seq also gets its leading newline chopped.
      appender.chopNext = true
    } else {
      appender.append(text)
    }
  }

  override def toString = {
    val appender = new StringAppender
    visit(appender)
    appender.toString.trim
  }
}

object CodeFormat extends Format[Code] {
  def raw(text: String): Code = new Code(text)
  def escape(text: String): Code = new Code(text)

  val empty: Code = new Code("")
  def fill(elements: immutable.Seq[Code]): Code = new Code(elements)
}
