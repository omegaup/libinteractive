package com.omegaup.libinteractive.target

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Random

import scala.collection.JavaConversions.asJavaIterable

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
	outputDirectory: Path = Paths.get("libinteractive"),
	parentLang: String = "c",
	pipeDirectories: Boolean = false,
	seed: Long = System.currentTimeMillis,
	sequentialIds: Boolean = false,
	verbose: Boolean = false
)

abstract class OutputPath(path: Path) {
	def install(root: Path): Unit
}

case class OutputDirectory(path: Path) extends OutputPath(path) {
	override def install(root: Path) {
		val directory = root.resolve(path).normalize
		if (!Files.exists(directory)) {
			Files.createDirectory(directory)
		}
	}
}

case class OutputFile(path: Path, contents: String) extends OutputPath(path) {
	override def install(root: Path) {
		Files.write(root.resolve(path), List(contents), StandardCharsets.UTF_8)
	}
}

case class OutputMakefile(path: Path, contents: String) extends OutputPath(path) {
	override def install(root: Path) {
		Files.write(path, List(contents), StandardCharsets.UTF_8)
	}
}

case class OutputLink(path: Path, target: Path) extends OutputPath(path) {
	override def install(root: Path) {
		val link = root.resolve(path)
		if (!Files.exists(link)) {
			Files.createSymbolicLink(link, link.relativize(root.resolve(target)))
		}
	}
}

case class MakefileRule(target: Path, requisites: Iterable[Path], command: String)

case class ExecDescription(args: Array[String], env: Map[String, String] = Map())

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

	def generate(): Iterable[OutputPath]
	def generateMakefileRules(): Iterable[MakefileRule]
	def generateRunCommands(): Iterable[ExecDescription]
}

/* vim: set noexpandtab: */
