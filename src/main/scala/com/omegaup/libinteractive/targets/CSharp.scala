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

class CSharp(idl: IDL, options: Options, input: Path, parent: Boolean)
		extends Target(idl, options) {
	override def extension() = "cs"

	override def generate() = {
		if (parent) {
			throw new UnsupportedOperationException
		} else {
			generateTemplates(options.moduleName, idl.interfaces,
					idl.main.name, List(idl.main), input) ++
			idl.interfaces.flatMap(generateInterface)
		}
	}

	override def generateInterface(interface: Interface) = {
		val csproj = templates.code.cs_csproj(options)
		List(
			new OutputDirectory(options.resolve(interface.name)),
			new OutputFile(options.resolve(interface.name,
				s"${interface.name}.csproj"), csproj.toString),
			generate(interface),
			generateLink(interface, input))
	}

	override def generateMakefileRules() = {
		if (parent) {
			generateMakefileRules(idl.main)
		} else {
			idl.interfaces.flatMap(generateMakefileRules)
		}
	}

	override def generateMakefileRules(interface: Interface) = {
		val configuration = (if (options.generateDebugTargets) "Debug" else "Release")
		val targetPath = (if (interface == idl.main) {
			options.relativeToRoot(interface.name, s"bin/${configuration}/netcoreapp2.0/${interface.name}.dll")
		} else {
			options.relativeToRoot(interface.name, s"bin/${configuration}/netcoreapp2.0/${interface.name}.dll")
		})
		val mainSourcePath = (if (interface == idl.main) {
			options.relativeToRoot(interface.name, s"${interface.name}.cs")
		} else {
			options.relativeToRoot(interface.name, s"${options.moduleName}.cs")
		})

		List(MakefileRule(
			List(targetPath),
			List(
				mainSourcePath,
				options.relativeToRoot(interface.name, s"${interface.name}_entry.cs")
			),
			Compiler.Dotnet, s"build ${options.relativeToRoot(interface.name)}" + (
				if (options.quiet) " > /dev/null" else ""
			)))
	}

	def dotnetExecutable() = {
		options.os match {
			case OS.Unix => "/usr/bin/dotnet"
			case OS.Windows => "csc"
		}
	}

	override def generateRunCommands() = {
		(if (parent) {
			List(idl.main)
		} else {
			idl.interfaces
		}).map(generateRunCommand)
	}

	override def generateRunCommand(interface: Interface) = {
		ExecDescription(
			args = Array(dotnetExecutable, "run", "--no-build", "--project",
				options.relativeToRoot(interface.name).toString),
			env = Map("DOTNET_CLI_TELEMETRY_OPTOUT" -> "1",
				        "HOME" -> ".")
		)
	}

	override def generateTemplates(moduleName: String,
			interfacesToImplement: Iterable[Interface], callableModuleName: String,
			callableInterfaces: Iterable[Interface], input: Path): Iterable[OutputPath] = {
		if (!options.generateTemplate) return List.empty[OutputPath]
		if (!options.force && Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
			throw new FileAlreadyExistsException(input.toString, null,
				"Refusing to overwrite file. Delete it or invoke with --force to override.")
		}

		val template = templates.code.cs_template(this, options,
			moduleName, callableInterfaces, interfacesToImplement)

		List(OutputFile(input, template.toString))
	}

	private def generate(interface: Interface) = {
		val cs = templates.code.cs(this, options, interface, idl.main)

		OutputFile(
			options.resolve(interface.name, s"${interface.name}_entry.cs"),
			cs.toString)
	}

	def formatLength(length: ArrayLength, function: Option[Function]) = {
		length match {
			case param: ParameterLength if !function.isEmpty =>
				s"${function.get.name}_${param.value}"
			case length: ArrayLength =>
				s"(${length.value})"
		}
	}

	def arrayDim(length: ArrayLength, function: Option[Function]) = {
		s"${formatLength(length, function)}"
	}

	def defaultValue(t: PrimitiveType) = {
		t.name match {
			case "bool" => "false"
			case "char" => "'\\0'"
			case "short" => "0"
			case "int" => "0"
			case "float" => "0.0f"
			case "long" => "0L"
			case "double" => "0.0"
		}
	}

	def formatPrimitive(t: PrimitiveType) = {
		t.name
	}

	def formatType(t: Type) = {
		t match {
			case arrayType: ArrayType =>
				s"${formatPrimitive(arrayType.primitive)}[" +
					arrayType.lengths.map(_ => "").mkString(",") + "]"
			case primitiveType: PrimitiveType =>
				s"${formatPrimitive(primitiveType)}"
		}
	}

	def formatParam(param: Parameter) = {
		s"${formatType(param.paramType)} ${param.name}"
	}

	def declareFunction(function: Function) = {
		s"${formatPrimitive(function.returnType)} ${function.name}(" +
			function.params.map(formatParam).mkString(", ") + ")"
	}

	def arrayLength(arrayType: ArrayType) = {
			arrayType.lengths.map(_.value).mkString(", ")
	}

	def declareVar(param: Parameter, function: Function) = {
		s"${formatType(param.paramType)} ${function.name}_${param.name}"
	}

	def allocateArray(array: ArrayType, function: Function) = {
		s"""new ${formatType(array.primitive)}[${array.lengths.map(arrayDim(_, Some(function))).mkString(", ")}]"""
	}

	def writePrimitive(primitive: PrimitiveType) = {
		primitive match {
			case PrimitiveType("short") => "Write"
			case PrimitiveType("int") => "Write"
			case PrimitiveType("long") => "Write"
			case PrimitiveType("char") => "Write"
			case PrimitiveType("float") => "Write"
			case PrimitiveType("double") => "Write"
			case PrimitiveType("bool") => "Write"
		}
	}

	def readPrimitive(primitive: PrimitiveType) = {
		primitive match {
			case PrimitiveType("short") => "ReadInt16"
			case PrimitiveType("int") => "ReadInt32"
			case PrimitiveType("long") => "ReadInt64"
			case PrimitiveType("char") => "ReadChar"
			case PrimitiveType("float") => "ReadSingle"
			case PrimitiveType("double") => "ReadDouble"
			case PrimitiveType("bool") => "ReadBoolean"
		}
	}

	def readArray(infd: String, param: Parameter, array: ArrayType,
			function: Option[Function], startingLevel: Int) = {
		var level = startingLevel
		val builder = new StringBuilder
		for (len <- array.lengths) {
			builder ++= "\t" * level +
					s"for (int __i$level = 0; __i$level < ${formatLength(len, function)}; " +
					s"__i$level++) {\n"
			level += 1
		}
		builder ++= "\t" * level +
			s"""${function.map(
				_.name + "_").mkString}${param.name}[${(startingLevel until level).map(
				idx => s"__i$idx").mkString(", ")}] = """ +
			s"$infd.${readPrimitive(array.primitive)}();\n"
		for (expr <- array.lengths) {
                        level -= 1
			builder ++= "\t" * level + "}\n"
		}
		builder
	}

	def writeArray(outfd: String, param: Parameter, array: ArrayType,
			startingLevel: Int) = {
		var level = startingLevel
		val builder = new StringBuilder
		for (expr <- array.lengths) {
			builder ++= "\t" * level +
					s"for (int __i$level = 0; __i$level < ${expr.value}; __i$level++) {\n"
			level += 1
		}
		builder ++= "\t" * level +
			s"$outfd.${writePrimitive(array.primitive)}(" +
			s"""${param.name}[${(startingLevel until level).map(
				idx => s"__i$idx").mkString(", ")}]);\n"""
		for (expr <- array.lengths) {
			level -= 1
			builder ++= "\t" * level + "}\n"
		}
		builder.toString
	}
}

/* vim: set noexpandtab: */
