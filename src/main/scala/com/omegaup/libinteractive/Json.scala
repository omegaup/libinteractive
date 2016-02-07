// Copyright (c) 2016 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive

import scala.collection.mutable.StringBuilder

object Json {
	def encode(s: String): String = {
		val buffer = new StringBuilder()
		buffer.append('"')
		s.foreach {
			c => {
				if (c == '/' || c == '\\' || c == '"') buffer.append('\\' + c)
				else if (c == '\b') buffer.append("\\b")
				else if (c == '\f') buffer.append("\\f")
				else if (c == '\n') buffer.append("\\n")
				else if (c == '\r') buffer.append("\\r")
				else if (c == '\t') buffer.append("\\t")
				else buffer.append(c)
			}
		}
		buffer.append('"')
		buffer.toString
	}
}

/* vim: set noexpandtab: */
