// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.target

import java.io.Closeable
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants

import scala.collection.JavaConversions.asJavaIterable
import scala.collection.mutable.MutableList

import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.idl.Interface

// DIY enum type, from https://gist.github.com/viktorklang/1057513
trait Enum {
	import java.util.concurrent.atomic.AtomicReference

	// This is a type that needs to be found in the implementing class
	type EnumVal <: Value

	// Stores our enum values
	private val _values = new AtomicReference(Vector[EnumVal]())

	// Adds an EnumVal to our storage, uses CCAS to make sure it's thread safe, returns
	// the ordinal
	private final def addEnumVal(newVal: EnumVal): Int = {
		import _values.{get, compareAndSet => CAS}

		val oldVec = get
		val newVec = oldVec :+ newVal
		if ((get eq oldVec) && CAS(oldVec, newVec)) {
			newVec.indexWhere(_ eq newVal)
		} else {
			addEnumVal(newVal)
		}
	}

	// Here you can get all the enums that exist for this type
	def values: Vector[EnumVal] = _values.get

	// This is the trait that we need to extend our EnumVal type with, it does the
	// book-keeping for us
	protected trait Value { self: EnumVal => // Enforce that no one mixes in Value in a
	                                         // non-EnumVal type
		// Adds the EnumVal and returns the ordinal
		final val ordinal = addEnumVal(this)

		// All enum values should have a name
		def name: String

		def find(name: String): Option[EnumVal] = _values.get.find(_.name == name)
		// And that name is used for the toString operation
		override def toString = name
		override def equals(other: Any) = this eq other.asInstanceOf[AnyRef]
		override def hashCode = 31 * (this.getClass.## + name.## + ordinal)
	}
}

object Command extends Enum {
	sealed trait EnumVal extends Value

	val Validate = new EnumVal { val name = "validate" }
	val Generate = new EnumVal { val name = "generate" }
	val GenerateAll = new EnumVal { val name = "generate-all" }
}

object OS extends Enum {
	sealed trait EnumVal extends Value

	val Unix = new EnumVal { val name = "unix" }
	val Windows = new EnumVal { val name = "windows" }
}

case class Options(
	childLang: String = "c",
	command: Command.EnumVal = Command.Validate,
	force: Boolean = false,
	generateTemplate: Boolean = false,
	idlFile: Path = null,
	legacyFlags: Boolean = false,
	makefile: Boolean = false,
	moduleName: String = "",
	outputDirectory: Path = Paths.get("libinteractive"),
	os: OS.EnumVal = OS.Unix,
	root: Path = Paths.get(".").normalize,
	packageDirectory: Path = Paths.get(".").normalize,
	packagePrefix: String = "",
	parentLang: String = "c",
	parentSource: Option[Path] = None,
	pipeDirectories: Boolean = false,
	quiet: Boolean = false,
	sampleFiles: List[String] = List("examples/sample.in"),
	seed: Long = System.currentTimeMillis,
	sequentialIds: Boolean = false,
	verbose: Boolean = false
)

abstract class OutputPath(val path: Path)
case class OutputDirectory(override val path: Path,
	val relative: Boolean = true) extends OutputPath(path)
case class OutputFile(override val path: Path, val contents: String,
	val relative: Boolean = true)	extends OutputPath(path)
case class OutputLink(override val path: Path, val target: Path)
		extends OutputPath(path)

class InstallVisitor(installPath: Path, root: Path) {
	def apply(outputPath: OutputPath) = outputPath match {
		case dir: OutputDirectory => {
			val directory = (if (dir.relative) {
				installPath.resolve(dir.path).normalize
			} else {
				dir.path
			})
			if (!Files.exists(directory)) {
				Files.createDirectories(directory)
			}
		}

		case file: OutputFile => {
			val destination = (if (file.relative) {
				installPath.resolve(file.path)
			} else {
				file.path
			})
			Files.write(destination, List(file.contents), StandardCharsets.UTF_8)
		}

		case link: OutputLink => { 
			val linkPath = installPath.resolve(link.path)
			if (!Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) {
				Files.createSymbolicLink(linkPath,
					linkPath.getParent.relativize(link.target))
			}
		}
	}
}

abstract class ArchiveVisitor(installPath: Path) extends Closeable {
	def apply(outputPath: OutputPath): Unit
}

class CompressedTarballVisitor(installPath: Path, tgzFilename: Path)
		extends ArchiveVisitor(installPath) {
	val bz2 = new BZip2CompressorOutputStream(new FileOutputStream(tgzFilename.toFile))
	val tar = new TarArchiveOutputStream(bz2)

	override def apply(outputPath: OutputPath) = outputPath match {
		case dir: OutputDirectory => {
			val directory = (if (dir.relative) {
				installPath.resolve(dir.path).normalize
			} else {
				dir.path
			})
			tar.putArchiveEntry(
				new TarArchiveEntry(directory.toString + "/", TarConstants.LF_DIR))
			tar.closeArchiveEntry
		}

		case file: OutputFile => {
			val entry = new TarArchiveEntry((if (file.relative) {
				installPath.resolve(file.path)
			} else {
				file.path
			}).toString, TarConstants.LF_NORMAL)
			val bytes = file.contents.getBytes(StandardCharsets.UTF_8)
			entry.setSize(bytes.length)
			entry.setUserId(1000)
			entry.setUserName("omegaup")
			entry.setModTime(System.currentTimeMillis)
			tar.putArchiveEntry(entry)
			tar.write(bytes, 0, bytes.length)
			tar.closeArchiveEntry
		}

		case link: OutputLink => {
			val linkPath = installPath.resolve(link.path)
			val entry = new TarArchiveEntry(linkPath.toString, TarConstants.LF_SYMLINK)
			entry.setLinkName(linkPath.getParent.relativize(link.target).toString)
			entry.setUserId(1000)
			entry.setUserName("omegaup")
			entry.setModTime(System.currentTimeMillis)
			tar.putArchiveEntry(entry)
			tar.closeArchiveEntry
		}
	}

	override def close() = {
		tar.close
	}
}

class ZipVisitor(installPath: Path, zipFilename: Path)
		extends ArchiveVisitor(installPath) {
	val zip = new ZipOutputStream(new FileOutputStream(zipFilename.toFile))

	override def apply(outputPath: OutputPath) = outputPath match {
		case dir: OutputDirectory => {
			val directory = (if (dir.relative) {
				installPath.resolve(dir.path).normalize
			} else {
				dir.path
			})
			zip.putNextEntry(new ZipEntry(directory.toString + "/"))
			zip.closeEntry
		}

		case file: OutputFile => {
			val entry = new ZipEntry((if (file.relative) {
				installPath.resolve(file.path)
			} else {
				file.path
			}).toString)
			val bytes = new ByteArrayOutputStream
			val writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)
			var lastChar = '\u0000'
			for (c <- file.contents) {
				if (c == '\n' && lastChar != '\r') {
					writer.write('\r')
				}
				lastChar = c
				writer.write(c)
			}
			writer.close
			val rawBytes = bytes.toByteArray
			entry.setSize(rawBytes.length)
			entry.setTime(System.currentTimeMillis)
			zip.putNextEntry(entry)
			zip.write(rawBytes, 0, rawBytes.length)
			zip.closeEntry
		}
	}

	override def close() = {
		zip.close
	}
}

case class ResolvedOutputLink(link: Path, target: Path)

abstract class OutputPathFilter {
	def apply(input: OutputPath): Option[OutputPath]
}

object NoOpFilter extends OutputPathFilter {
	override def apply(input: OutputPath) = Some(input)
}

abstract class LinkFilter extends OutputPathFilter {
	def resolvedLinks(): Iterable[ResolvedOutputLink]
}

object NoOpLinkFilter extends LinkFilter {
	override def resolvedLinks() = List.empty[ResolvedOutputLink]
	override def apply(input: OutputPath) = Some(input)
}

class WindowsLinkFilter(root: Path) extends LinkFilter {
	private val links = MutableList.empty[ResolvedOutputLink]

	override def resolvedLinks() = links
	override def apply(input: OutputPath) = {
		input match {
			case link: OutputLink => {
				val resolvedLink = root.resolve(link.path)
				links += ResolvedOutputLink(
					resolvedLink, link.target)
				None
			}
			case path: OutputPath => Some(path)
		}
	}
}

object Compiler extends Enumeration {
	type Compiler = Value
	val Gcc = Value("gcc")
	val Gxx = Value("g++")
	val Fpc = Value("fpc")
	val Javac = Value("javac")
	val Python = Value("python")
	val Ruby = Value("ruby")
}
import Compiler.Compiler

case class MakefileRule(target: Path, requisites: Iterable[Path], compiler: Compiler,
		params: String, debug: Boolean = false)

case class ExecDescription(args: Array[String],
	debug_args: Option[Array[String]] = None, env: Map[String, String] = Map())

abstract class Target(idl: IDL, options: Options) {
	val message = "Auto-generated by libinteractive. Do not edit."
	val rand = new Random(options.seed)
	val functionIds = idl.interfaces.flatMap (interface => {
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

	def pipeFilename(interface: Interface, caller: Interface, input: Boolean) = {
		(if (options.pipeDirectories) {
			s"${interface.name}_pipes/"
		} else {
			(options.os match {
				case OS.Unix => ""
				case OS.Windows => s"\\\\\\\\.\\\\pipe\\\\libinteractive_${caller.name}_"
			}) + s"${interface.name}_"
		}) + (input match {
			case true => "in"
			case false => "out"
		})
	}

	def pipeName(interface: Interface, input: Boolean) = {
		s"__${interface.name}_" + (input match {
			case true => "in"
			case false => "out"
		})
	}

	def relativeToRoot(path: Path) = {
		(if (options.root.toString.length == 0) {
			path
		} else {
			options.root.relativize(path)
		}).normalize
	}

	def generate(): Iterable[OutputPath]
	def extension(): String
	def generateMakefileRules(): Iterable[MakefileRule]
	def generateRunCommands(): Iterable[ExecDescription]

	protected def generateLink(interface: Interface, input: Path): OutputPath = {
		val moduleFile = s"${options.moduleName}.$extension"
		new OutputLink(Paths.get(interface.name, moduleFile), input)
	}
	protected def generateTemplates(moduleName: String,
			interfacesToImplement: Iterable[Interface], callableModuleName: String,
			callableInterfaces: Iterable[Interface], input: Path): Iterable[OutputPath]
}

object Generator {
	def generate(idl: IDL, options: Options, problemsetter: Path, contestant: Path) = {
		val parent = target(options.parentLang, idl, options, problemsetter, true)
		val child = target(options.childLang, idl, options, contestant, false)

		val originalTargets = List(parent, child)
		val originalOutputs = originalTargets.flatMap(_.generate)
		if (options.makefile) {
			val filter = options.os match {
				case OS.Unix => NoOpLinkFilter
				case OS.Windows => new WindowsLinkFilter(options.outputDirectory)
			}
			val filteredOutputs = originalOutputs.flatMap(filter.apply)
			filteredOutputs ++ new Makefile(idl,
				originalTargets.flatMap(_.generateMakefileRules),
				originalTargets.flatMap(_.generateRunCommands),
				filter.resolvedLinks, options).generate
		} else {
			originalOutputs
		}
	}

	def target(lang: String, idl: IDL, options: Options, input: Path,
			parent: Boolean): Target = {
		lang match {
			case "c" => new C(idl, options, input, parent)
			case "cpp" => new Cpp(idl, options, input, parent)
			case "cpp11" => new Cpp(idl, options, input, parent)
			case "java" => new Java(idl, options, input, parent)
			case "pas" => new Pascal(idl, options, input, parent)
			case "py" => new Python(idl, options, input, parent)
			case "rb" => new Ruby(idl, options)
		}
	}
}

/* vim: set noexpandtab: */
