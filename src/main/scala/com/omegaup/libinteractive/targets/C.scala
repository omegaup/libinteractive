// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.target

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.FileAlreadyExistsException

import com.omegaup.libinteractive.idl._
import com.omegaup.libinteractive.templates

class C(idl: IDL, options: Options, input: Path, parent: Boolean)
		extends Target(idl, options) {
	override def extension() = "c"

	def executableExtension() = options.os match {
		case OS.Windows => ".exe"
		case _ => ""
	}

	override def generate() = {
		if (parent) {
			val mainFile = s"${idl.main.name}.$extension"
			List(
				new OutputDirectory(Paths.get(idl.main.name)),
				new OutputLink(Paths.get(idl.main.name, mainFile), input),
				generateMainHeader,
				generateMainFile)
		} else {
			generateTemplates(options.moduleName, idl.interfaces,
					idl.main.name, List(idl.main), input) ++
			idl.interfaces.flatMap(interface =>
				List(
					new OutputDirectory(Paths.get(interface.name)),
					generateHeader(interface),
					generate(interface),
					generateLink(interface, input))
			)
		}
	}

	override def generateMakefileRules() = {
		if (parent) {
			List(MakefileRule(Paths.get(idl.main.name, idl.main.name + executableExtension),
				List(
					outputResolve(Paths.get(idl.main.name, s"${idl.main.name}.$extension")),
					outputResolve(Paths.get(idl.main.name, s"${idl.main.name}_entry.$extension"))
				),
				compiler, s"$cflags -o $$@ $$^ -lm -O2 -g $ldflags -Wno-unused-result"))
		} else {
			idl.interfaces.map(interface =>
				MakefileRule(Paths.get(interface.name, interface.name + executableExtension),
					List(
						input,
						outputResolve(Paths.get(interface.name, s"${interface.name}_entry.$extension"))
					),
					compiler, s"$cflags -o $$@ $$^ -lm -O2 -g $ldflags -Wno-unused-result -I" + relativeToRoot(outputResolve(interface.name)))) ++
			idl.interfaces.map(interface =>
				MakefileRule(Paths.get(interface.name, interface.name + "_debug" + executableExtension),
					List(
						input,
						outputResolve(Paths.get(interface.name, s"${interface.name}_entry.$extension"))
					),
					compiler, s"$cflags -o $$@ $$^ -lm -g $ldflags -Wno-unused-result -I" + relativeToRoot(outputResolve(interface.name)),
					debug = true))
		}
	}

	private def gdbserverPath = {
		options.os match {
			case OS.Unix => "/usr/bin/gdbserver"
			case OS.Windows => "gdbserver.exe"
		}
	}

	private def runCommand(interface: Interface, suffix: String = "") = {
		relativeToRoot(
			options.outputDirectory.resolve(
				Paths.get(interface.name, interface.name + suffix + executableExtension)
			)
		).toString
	}

	override def generateRunCommands() = {
		if (parent) {
			List(ExecDescription(Array(runCommand(idl.main))))
		} else {
			List(idl.interfaces.head).map(
				interface => ExecDescription(
					args = Array(runCommand(interface)),
					debug_args = Some(
						Array(gdbserverPath, "127.0.0.1:8042", runCommand(interface, "_debug"))
					)
				)
			) ++
			idl.interfaces.tail.map(
				interface => ExecDescription(Array(runCommand(interface)))
			)
		}
	}

	override def generateTemplates(moduleName: String,
			interfacesToImplement: Iterable[Interface], callableModuleName: String,
			callableInterfaces: Iterable[Interface], input: Path): Iterable[OutputPath] = {
		if (!options.generateTemplate) return List.empty[OutputPath]
		if (!options.force && Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
			throw new FileAlreadyExistsException(input.toString, null,
				"Refusing to overwrite file. Delete it or invoke with --force to override.")
		}

		val template = templates.code.c_template(this, options, callableInterfaces, interfacesToImplement)

		List(OutputFile(input, template.toString, false))
	}

	private def generateHeader(interface: Interface) = {
		val header = templates.code.c_header(this, List(interface, idl.main))

		OutputFile(
			Paths.get(interface.name, s"${options.moduleName}.h"),
			header.toString)
	}

	private def generate(interface: Interface) = {
		val child = templates.code.c(this, idl, options, interface)
		
		OutputFile(
			Paths.get(interface.name, s"${interface.name}_entry.$extension"),
			child.toString)
	}

	private def generateMainHeader() = {
		val header = templates.code.c_header(this, idl.allInterfaces)

		OutputFile(
			Paths.get(idl.main.name, s"${options.moduleName}.h"),
			header.toString)
	}

	private def generateMainFile() = {
		val c = templates.code.c_main(this, options, idl)
		OutputFile(
			Paths.get(idl.main.name, s"${idl.main.name}_entry.$extension"),
			c.toString)
	}

	def compiler() = Compiler.Gcc

	def cflags() = "-std=c99"

	def ldflags() = {
		if (parent) {
			options.os match {
				case OS.Unix => "-Wl,-e__entry"
				case OS.Windows => "-Wl,-e___entry"
			}
		} else {
			""
		}
	}

	def arrayDim(length: ArrayLength) = s"[${length.value}]"

	def defaultValue(t: PrimitiveType) = {
		t.name match {
			case "bool" => "false"
			case "char" => "'\\0'"
			case "short" => "0"
			case "int" => "0"
			case "float" => "0.0f"
			case "long" => "0LL"
			case "double" => "0.0"
		}
	}

	def formatPrimitive(t: PrimitiveType) = {
		t.name match {
			case "long" => "long long"
			case primitive: String => primitive
		}
	}

	def formatType(t: Type) = {
		t match {
			case arrayType: ArrayType =>
				s"${formatPrimitive(arrayType.primitive)}(*)" +
					arrayType.lengths.tail.map(arrayDim).mkString
			case primitiveType: PrimitiveType =>
				s"${formatPrimitive(primitiveType)}"
		}
	}

	def formatParam(param: Parameter) = {
		param.paramType match {
			case arrayType: ArrayType =>
				s"${formatPrimitive(arrayType.primitive)} ${param.name}[]" +
					arrayType.lengths.tail.map(arrayDim).mkString
			case primitiveType: PrimitiveType =>
				s"${formatPrimitive(primitiveType)} ${param.name}"
		}
	}

	def formatLength(length: ArrayLength, function: Option[Function]) = {
		length match {
			case param: ParameterLength if !function.isEmpty =>
				s"${function.get.name}_${param.value}"
			case length: ArrayLength =>
				s"(${length.value})"
		}
	}

	def fieldLength(fieldType: Type, function: Option[Function] = None) = {
		fieldType match {
			case primitiveType: PrimitiveType =>
				s"sizeof(${formatPrimitive(primitiveType)})"
			case arrayType: ArrayType =>
				s"sizeof(${formatPrimitive(arrayType.primitive)}) * " +
					arrayType.lengths.map(formatLength(_, function)).mkString(" * ")
		}
	}

	def declareFunction(function: Function) = {
		s"${formatPrimitive(function.returnType)} ${function.name}(" +
			function.params.map(formatParam).mkString(", ") + ")"
	}

	def declareVar(param: Parameter, function: Function) = {
		param.paramType match {
			case array: ArrayType =>
				s"${formatPrimitive(array.primitive)} (*${function.name}_${param.name})" +
					array.lengths.tail.map(arrayDim).mkString
			case primitive: PrimitiveType =>
				s"${formatPrimitive(primitive)} ${function.name}_${param.name}"
		}
	}
}

class Cpp(idl: IDL, options: Options, input: Path, parent: Boolean)
		extends C(idl, options, input, parent) {
	override def extension() = "cpp"

	override def compiler() = Compiler.Gxx

	override def cflags() = options.legacyFlags match {
		case false => "-std=c++11"
		case true => "-std=c++0x"
	}
}

/* vim: set noexpandtab: */
