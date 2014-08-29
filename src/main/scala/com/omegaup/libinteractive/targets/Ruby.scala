package com.omegaup.libinteractive.target

import java.nio.file.Path

import scala.collection.mutable.StringBuilder

import com.omegaup.libinteractive.idl._

class Ruby(idl: IDL, options: Options) extends Target(idl, options) {
	override def extension() = ???
	override def generateMakefileRules() = ???
	override def generateRunCommands() = ???
	override def generate() = ???
	override def generateTemplate(interface: Interface, input: Path) = ???
}

/* vim: set noexpandtab: */
