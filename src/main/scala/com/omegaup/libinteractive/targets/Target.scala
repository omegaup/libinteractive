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
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.Random
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants

import scala.collection.JavaConverters._
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

	object Validate extends EnumVal { val name = "validate" }
	object Json extends EnumVal { val name = "json" }
	object Generate extends EnumVal { val name = "generate" }
	object GenerateAll extends EnumVal { val name = "generate-all" }
}

object OS extends Enum {
	sealed trait EnumVal extends Value

	object Unix extends EnumVal { val name = "unix" }
	object Windows extends EnumVal { val name = "windows" }
}

case class Options(
	childLang: String = "c",
	command: Command.EnumVal = Command.Validate,
	force: Boolean = false,
	generateTemplate: Boolean = false,
	generateDebugTargets: Boolean = true,
	idlFile: Path = null,
	json: Boolean = false,
	legacyFlags: Boolean = false,
	metadata: Boolean = false,
	makefile: Boolean = false,
	moduleName: String = null,
	libraryDirectory: Path = Paths.get("libinteractive"),
	os: OS.EnumVal = OS.Unix,
	preferOriginalSources: Boolean = true,
	root: Path = Paths.get(".").normalize,
	packageDirectory: Path = Paths.get(".").normalize,
	packagePrefix: String = "",
	parentLang: String = null,
	parentSource: Option[Path] = None,
	pipeLocation: Path = Paths.get("."),
	pipeDirectories: Boolean = false,
	quiet: Boolean = false,
	sampleFiles: List[String] = List("examples/sample.in"),
	// The first 8 bytes of the SHA-1 hash of 'libinteractive'.
	seed: Long = 0x7377fa52ad19a144l,
	sequentialIds: Boolean = false,
	shiftTimeForZip: Boolean = false,
	transact: Boolean = false,
	verbose: Boolean = false
) {
	private def resolve(path: Path, library: Boolean): Path = {
		var resolvedPath = (if (library && libraryDirectory.toString.length > 0) {
			libraryDirectory.resolve(path)
		} else {
			path
		})
		(if (root.toString.length == 0) {
			resolvedPath
		} else {
			root.resolve(resolvedPath)
		}).normalize
	}

	def resolve(path: Path): Path = {
		resolve(path, true)
	}

	def resolve(first: String, more: String*): Path = {
		resolve(Paths.get(first, more:_*), true)
	}

	def rootResolve(path: Path): Path = {
		resolve(path, false)
	}

	def rootResolve(first: String, more: String*): Path = {
		resolve(Paths.get(first, more:_*), false)
	}

	def relativize(path: Path): Path = {
		if (root.toString.length == 0) {
			path
		} else {
			root.relativize(path)
		}
	}

	def relativeToRoot(path: Path): Path = {
		relativize(resolve(path))
	}

	def relativeToRoot(first: String, more: String*): Path = {
		relativeToRoot(Paths.get(first, more:_*))
	}
}

abstract class OutputPath(val path: Path)
case class OutputDirectory(override val path: Path) extends OutputPath(path)
case class OutputFile(override val path: Path,
	val contents: String, val permissions: java.util.Set[PosixFilePermission] = null) extends OutputPath(path)
case class OutputLink(override val path: Path, val target: Path)
		extends OutputPath(path)

class InstallVisitor {
	def apply(outputPath: OutputPath) = outputPath match {
		case dir: OutputDirectory => {
			if (!Files.exists(dir.path)) {
				Files.createDirectories(dir.path)
			}
		}

		case file: OutputFile => {
			Files.write(file.path, List(file.contents), StandardCharsets.UTF_8)
			if (file.permissions != null) {
				Files.setPosixFilePermissions(file.path, file.permissions)
			}
		}

		case link: OutputLink => { 
			if (!Files.exists(link.path, LinkOption.NOFOLLOW_LINKS)) {
				Files.createSymbolicLink(link.path,
					link.path.getParent.relativize(link.target))
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
			tar.putArchiveEntry(
				new TarArchiveEntry(installPath.resolve(dir.path).toString + "/",
					TarConstants.LF_DIR))
			tar.closeArchiveEntry
		}

		case file: OutputFile => {
			val entry = new TarArchiveEntry(installPath.resolve(file.path).toString,
				TarConstants.LF_NORMAL)
			val bytes = file.contents.getBytes(StandardCharsets.UTF_8)
			entry.setSize(bytes.length)
			entry.setUserId(1000)
			entry.setUserName("omegaup")
			entry.setModTime(System.currentTimeMillis)
			if (file.permissions != null) {
				entry.setMode(this.toMode(file.permissions))
			}
			tar.putArchiveEntry(entry)
			tar.write(bytes, 0, bytes.length)
			tar.closeArchiveEntry
		}

		case link: OutputLink => {
			val linkPath = installPath.resolve(link.path)
			val entry = new TarArchiveEntry(linkPath.toString, TarConstants.LF_SYMLINK)
			entry.setLinkName(linkPath.getParent.relativize(
				installPath.resolve(link.target)).toString)
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

	def toMode(permissions: java.util.Set[PosixFilePermission]) = {
		var sum = 0
		for (permission <- permissions.asScala) {
			sum |= (permission match {
				case PosixFilePermission.OWNER_READ => (1 << 8)
				case PosixFilePermission.OWNER_WRITE => (1 << 7)
				case PosixFilePermission.OWNER_EXECUTE => (1 << 6)
				case PosixFilePermission.GROUP_READ => (1 << 5)
				case PosixFilePermission.GROUP_WRITE => (1 << 4)
				case PosixFilePermission.GROUP_EXECUTE => (1 << 3)
				case PosixFilePermission.OTHERS_READ => (1 << 2)
				case PosixFilePermission.OTHERS_WRITE => (1 << 1)
				case PosixFilePermission.OTHERS_EXECUTE => 1
			})
		}
		sum
	}
}

class ZipVisitor(installPath: Path, zipFilename: Path, shiftTimeZone: Boolean)
		extends ArchiveVisitor(installPath) {
	val zip = new ZipOutputStream(new FileOutputStream(zipFilename.toFile))
	val curTz = TimeZone.getDefault
	val gmtNeg12  = TimeZone.getTimeZone("GMT-12")
	val timestamp = System.currentTimeMillis

	private def getTime() = {
		timestamp + (if (shiftTimeZone) {
			gmtNeg12.getOffset(timestamp) - curTz.getOffset(timestamp)
		} else {
			0
		})
	}

	override def apply(outputPath: OutputPath) = outputPath match {
		case dir: OutputDirectory => {
			val entry = new ZipEntry(dir.path.toString + "/")
			entry.setTime(getTime)
			zip.putNextEntry(entry)
			zip.closeEntry
		}

		case file: OutputFile => {
			val entry = new ZipEntry(file.path.toString)
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
			entry.setTime(getTime)
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

class WindowsLinkFilter extends LinkFilter {
	private val links = MutableList.empty[ResolvedOutputLink]

	override def resolvedLinks() = links
	override def apply(input: OutputPath) = {
		input match {
			case link: OutputLink => {
				val resolvedLink = link.path
				links += ResolvedOutputLink(
					resolvedLink, link.target)
				None
			}
			case path: OutputPath => Some(path)
		}
	}
}

object WindowsNewlineFilter extends OutputPathFilter {
	override def apply(input: OutputPath) = {
		input match {
			case file: OutputFile => {
				Some(OutputFile(
					file.path,
					file.contents.replace("\r", "").replace("\n", "\r\n")
				))
			}
			case path: OutputPath => Some(path)
		}
	}
}

class ReplacementFilter(replacements: List[OutputFile]) extends OutputPathFilter {
	override def apply(input: OutputPath) = {
		input match {
			case file: OutputFile => {
				replacements.find(_.path == file.path) match {
					case Some(replacement) => Some(replacement)
					case None => Some(file)
				}
			}
			case path: OutputPath => Some(path)
		}
	}
}

case class GeneratedInterface(interface: Interface, files: Iterable[OutputFile],
	makefileRules: Iterable[MakefileRule], executableDescription: ExecDescription,
	templateSource: String)

object Compiler extends Enumeration {
	type Compiler = Value
	val Dotnet = Value("dotnet")
	val Gcc = Value("gcc")
	val Gxx = Value("g++")
	val Fpc = Value("fpc")
	val Javac = Value("javac")
	val Python = Value("python")
	val Ruby = Value("ruby")
}
import Compiler.Compiler

case class MakefileRule(target: Iterable[Path], requisites: Iterable[Path], compiler: Compiler,
		params: List[String], flags: List[String] = List(), debug: Boolean = false)

case class ExecDescription(args: Array[String],
	debug_args: Option[Array[String]] = None, env: Map[String, String] = Map())

abstract class Target(idl: IDL, options: Options) {
	val message = "Auto-generated by libinteractive. Do not edit."
	private val rand = new Random(options.seed)
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
			cookieId()
		}
	}

	def cookieId() = rand.nextInt(0x7FFFFFFF)

	def transactFilename(interface: Interface) = {
		options.pipeLocation.resolve(
			if (options.pipeDirectories) {
				s"${interface.name}_transact/transact"
			} else {
				s"${interface.name}_transact"
			}
		).toString
	}

	def shmFilename(interface: Interface) = {
		options.pipeLocation.resolve(
			if (options.pipeDirectories) {
				s"${interface.name}_transact/shm"
			} else {
				s"${interface.name}_shm"
			}
		).toString
	}

	def pipeFilename(interface: Interface, caller: Interface, input: Boolean) = {
		(if (options.pipeDirectories) {
			options.pipeLocation.resolve(s"${interface.name}_pipes").toString + "/"
		} else {
			(options.os match {
				case OS.Unix => options.pipeLocation.toString + "/"
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

	def transactName(interface: Interface) = {
		s"__${interface.name}_transact"
	}

	def generate(): Iterable[OutputPath]
	def generateTemplateSource(): String
	def generateInterface(interface: Interface): Iterable[OutputPath]
	def extension(): String
	def generateMakefileRules(): Iterable[MakefileRule]
	def generateMakefileRules(interface: Interface): Iterable[MakefileRule]
	def generateRunCommands(): Iterable[ExecDescription]
	def generateRunCommand(interface: Interface): ExecDescription

	protected def generateLink(interface: Interface, input: Path): OutputPath = {
		val moduleFile = s"${options.moduleName}.$extension"
		new OutputLink(options.resolve(interface.name, moduleFile), input)
	}
	protected final def generateTemplates(input: Path): Iterable[OutputPath] = {
		if (!options.generateTemplate) return List.empty[OutputPath]
		if (!options.force && Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
			throw new FileAlreadyExistsException(input.toString, null,
				"Refusing to overwrite file. Delete it or invoke with --force to override.")
		}

		List(OutputFile(input, generateTemplateSource()))
	}
}

object Generator {
	def generate(idl: IDL, options: Options, problemsetter: Path, contestant: Path) = {
		val parent = target(options.parentLang, idl, options, problemsetter, true)
		val child = target(options.childLang, idl, options, contestant, false)

		val originalTargets = List(parent, child)
		val originalOutputs = originalTargets.flatMap(_.generate)
		(if (options.makefile) {
			val filter = options.os match {
				case OS.Unix => NoOpLinkFilter
				case OS.Windows => new WindowsLinkFilter
			}
			val filteredOutputs = originalOutputs.flatMap(filter.apply)
			filteredOutputs ++ new Makefile(idl,
				originalTargets.flatMap(_.generateMakefileRules),
				originalTargets.flatMap(_.generateRunCommands),
				filter.resolvedLinks, options).generate
		} else {
			originalOutputs
		}).flatMap((options.os match {
				case OS.Unix => NoOpFilter
				case OS.Windows => WindowsNewlineFilter
		}).apply)
	}

	def generateInterface(idl: IDL, options: Options, input: Path,
			lang: String, interface: Interface): GeneratedInterface = {
		val linkFilter = options.os match {
			case OS.Unix => NoOpLinkFilter
			case OS.Windows => new WindowsLinkFilter
		}
		val newlineFilter = options.os match {
			case OS.Unix => NoOpFilter
			case OS.Windows => WindowsNewlineFilter
		}
		val currentTarget = target(lang, idl, options, input, interface == idl.main)
		GeneratedInterface(
			interface = interface,
			files = currentTarget.generateInterface(interface)
				.flatMap(linkFilter.apply)
				.flatMap(newlineFilter.apply)
				.flatMap(_ match {
					case x: OutputFile => Some(x)
					case _ => None
				}),
			makefileRules = currentTarget.generateMakefileRules(interface).filter(!_.debug || options.generateDebugTargets),
			executableDescription = currentTarget.generateRunCommand(interface),
			templateSource = currentTarget.generateTemplateSource
		)
	}

	def target(lang: String, idl: IDL, options: Options, input: Path,
			parent: Boolean): Target = {
		lang match {
			case "c" => new C(idl, options, input, parent)
			case "cs" => new CSharp(idl, options, input, parent)
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
