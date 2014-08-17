package com.omegaup.libinteractive.target

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Random

import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.idl.Interface

object Command extends Enumeration {
	type Command = Value
	val Verify, Generate = Value
}
import Command.Command

case class Options(
	childLang: String = "c",
	command: Command = Command.Verify,
	idlFile: File = null,
	makefile: Boolean = false,
	moduleName: String = "",
	outputDirectory: File = new File("."),
	parentLang: String = "c",
	pipeDirectories: Boolean = false,
	seed: Long = System.currentTimeMillis,
	sequentialIds: Boolean = false,
	verbose: Boolean = false
)

case class OutputFile(filename: String, contents: String) {
	override def toString() = {
		s"""OutputFile("$filename", "${contents}")"""
	}
}

case class MakefileRule(target: String, requisites: Iterable[String], command: String)

abstract class Target(idl: IDL, options: Options) {
	protected val rand = new Random(options.seed)
	protected val functionIds = idl.interfaces.flatMap (interface => {
		interface.functions.map(
			function => (idl.main.name, interface.name, function.name) -> nextId) ++
		idl.main.functions.map(
			function => (interface.name, idl.main.name, function.name) -> nextId)
	}).toMap

	private var currentId = 0
	private def nextId() = {
		if (options.sequentialIds) {
			currentId += 1
			currentId
		} else {
			rand.nextInt
		}
	}

	protected def pipeFilename(interface: Interface) = {
		if (options.pipeDirectories) {
			interface.name match {
				case "Main" => "Main_pipes/out"
				case name: String => s"${name}_pipes/in"
			}
		} else {
			interface.name match {
				case "Main" => "out"
				case name: String => s"${name}_in"
			}
		}
	}

	protected def pipeName(interface: Interface) = {
		interface.name match {
			case "Main" => "__out"
			case name: String => s"__${name}_in"
		}
	}

	def generate(): Iterable[OutputFile]
	def generateMakefileRules(): Iterable[MakefileRule]
	def generateRunCommands(): Iterable[Array[String]]
	def createWorkDirs(): Unit

	protected def createWorkDir(interface: Interface, originalSource: String) = {
		val workdir = new File(options.outputDirectory, interface.name)
		if (!workdir.exists) {
			workdir.mkdir
		}
		val targetSource = new File(workdir, originalSource)
		if (!targetSource.exists) {
			Files.createSymbolicLink(
				targetSource.toPath,
				Paths.get("..", originalSource))
		}
	}
}

/* vim: set noexpandtab: */
