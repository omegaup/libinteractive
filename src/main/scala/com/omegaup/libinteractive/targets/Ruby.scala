// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.target

import java.nio.file.Path

import scala.collection.mutable.StringBuilder

import com.omegaup.libinteractive.idl._

class Ruby(idl: IDL, options: Options) extends Target(idl, options) {
	override def extension() = ???
	override def generateMakefileRules() = ???
	override def generateRunCommands() = ???
	override def generate() = ???
	protected def generateTemplates(moduleName: String,
			interfacesToImplement: Iterable[Interface], callableModuleName: String,
			callableInterfaces: Iterable[Interface], input: Path) = ???
}

/* vim: set noexpandtab: */
