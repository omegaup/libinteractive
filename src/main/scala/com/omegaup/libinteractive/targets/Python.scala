// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.target

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

import scala.collection.mutable.StringBuilder

import com.omegaup.libinteractive.idl._
import com.omegaup.libinteractive.templates

class Python(idl: IDL, options: Options, input: Path, parent: Boolean)
		extends Target(idl, options) {
	override def extension() = "py"

	override def generate() = {
		if (parent) {
			val mainFile = s"${idl.main.name}.py"
			List(
				new OutputDirectory(options.resolve(idl.main.name)),
				new OutputLink(options.resolve(idl.main.name, mainFile), input),
				generateMainFile,
				generateMainEntry)
		} else {
			generateTemplates(input) ++
			idl.interfaces.flatMap(generateInterface)
		}
	}

	override def generateInterface(interface: Interface) = {
		if (interface == idl.main) {
			val mainFile = s"${idl.main.name}.$extension"
			List(
				new OutputDirectory(options.resolve(idl.main.name)),
				new OutputLink(options.resolve(idl.main.name, mainFile), input),
				generateMainFile,
				generateMainEntry)
		} else {
			List(
				new OutputDirectory(options.resolve(interface.name)),
				generateLib(interface),
				generate(interface),
				generateLink(interface, input))
		}
	}

	def pythonExecutable() = {
		options.os match {
			case OS.Unix => "/usr/bin/python"
			case OS.Windows => "python"
		}
	}

	override def generateMakefileRules() = {
		(if (parent) {
			List(idl.main)
		} else {
			idl.interfaces
		}).flatMap(generateMakefileRules)
	}

	override def generateMakefileRules(interface: Interface) = {
		List(MakefileRule(
			target = List(
				options.relativeToRoot(interface.name, s"${idl.main.name}.pyc"),
				options.relativeToRoot(interface.name, s"${options.moduleName}.pyc"),
				options.relativeToRoot(interface.name, s"${interface.name}_entry.pyc")
			),
			requisites = List(
				options.relativeToRoot(interface.name, s"${idl.main.name}.py"),
				options.relativeToRoot(interface.name, s"${options.moduleName}.py"),
				options.relativeToRoot(interface.name, s"${interface.name}_entry.py")
			),
			compiler = Compiler.Python,
			params = List("-m", "py_compile", "$^")
		))
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
			Array(
				pythonExecutable,
				options.relativeToRoot(interface.name,
					s"${interface.name}_entry.py").toString
			)
		)
	}

	override def generateTemplateSource(): String = {
		templates.code.python_template(this, options,
			List(idl.main), idl.interfaces).toString
	}

	def structFormat(formatType: Type): String = {
		formatType match {
			case primitiveType: PrimitiveType => primitiveType match {
				case PrimitiveType("short") => "'h'"
				case PrimitiveType("int") => "'i'"
				case PrimitiveType("long") => "'q'"
				case PrimitiveType("char") => "'c'"
				case PrimitiveType("float") => "'f'"
				case PrimitiveType("double") => "'d'"
				case PrimitiveType("bool") => "'?'"
			}
			case arrayType: ArrayType => {
				"'%d" + structFormat(arrayType.primitive).charAt(1) + "' % " +
						arrayLength(arrayType)
			}
		}
	}

	def defaultValue(t: PrimitiveType) = {
		t.name match {
			case "bool" => "False"
			case "char" => "'\\x00'"
			case "short" => "0"
			case "int" => "0"
			case "float" => "0.0f"
			case "long" => "0L"
			case "double" => "0.0"
		}
	}

	def arrayLength(arrayType: ArrayType) = {
			arrayType.lengths.map(_.value).mkString(" * ")
	}

	def fieldLength(primitiveType: Type): String = {
		primitiveType match {
			case PrimitiveType("bool") => "1"
			case PrimitiveType("char") => "1"
			case PrimitiveType("short") => "2"
			case PrimitiveType("int") => "4"
			case PrimitiveType("float") => "4"
			case PrimitiveType("long") => "8"
			case PrimitiveType("double") => "8"
		}
	}

	def declareFunction(function: Function) = {
		s"def ${function.name}(" + function.params.map(_.name).mkString(", ") + ")"
	}

	private def generateMainEntry() = {
		val builder = new StringBuilder
		builder ++= s"""#!/usr/bin/python
# $message

"${'"'}"Trampoline for libinteractive's Main."${'"'}"

import runpy
import sys

sys.path[0] = "${options.relativeToRoot(idl.main.name)}"

# This is needed so that the ${idl.main.name} module believes it is the main
# entrypoint. By invoking this file instead of ${idl.main.name} directly, we
# avoid an import cycle.
runpy.run_module("${idl.main.name}", run_name="__main__")"""
		OutputFile(
			options.resolve(idl.main.name, s"${idl.main.name}_entry.py"),
			builder.mkString)
	}

	private def generateMainFile() = {
		val main = templates.code.python_main(this, options, idl) 

		OutputFile(
			options.resolve(idl.main.name, s"${options.moduleName}.py"),
			main.toString)
	}

	private def generate(interface: Interface) = {
		val builder = new StringBuilder
		builder ++= s"""#!/usr/bin/python
# $message

"${'"'}"Trampoline for libinteractive's ${interface.name}."${'"'}"

# This is needed so that the ${interface.name} module believes it is the main
# entrypoint. By invoking this file instead of ${interface.name} directly, we
# avoid an import cycle.
import ${idl.main.name}  # pylint: disable=unused-import"""
		OutputFile(
			options.resolve(interface.name, s"${interface.name}_entry.py"),
			builder.mkString)
	}

	private def generateLib(interface: Interface) = {
		val python = templates.code.python(this, options, interface, idl.main)

		OutputFile(
			options.resolve(interface.name, s"${idl.main.name}.py"),
			python.toString)
	}

	def formatLength(length: ArrayLength, function: Option[Function]) = {
		length match {
			case param: ParameterLength if !function.isEmpty =>
				s"${function.get.name}_${param.value}"
			case length: ArrayLength =>
				s"(${length.value})"
		}
	}

	def readArray(infd: String, primitive: PrimitiveType,
			lengths: Iterable[ArrayLength], function: Option[Function] = None,
			depth: Integer = 0): String = {
		lengths match {
			case head :: Nil =>
				s"__readarray($infd, ${structFormat(primitive)}, " +
					s"${formatLength(head, function)} * ${fieldLength(primitive)})"
			case head :: tail =>
				s"[${readArray(infd, primitive, tail, function, depth + 1)} " +
					s"for __r$depth in range(${formatLength(head, function)})]"
		}
	}

	def writeArray(outfd: String, name: String, primitive: PrimitiveType,
			lengths: Iterable[ArrayLength], depth: Integer = 1): String = {
		lengths match {
			case head :: Nil =>
				"    " * depth + s"$outfd.write(struct.pack(" +
				s"'%d${structFormat(primitive).charAt(1)}' % (${head.value}), *$name))"
			case head :: tail =>
				"    " * depth + s"for __r$depth in $name:\n" +
					writeArray(outfd, s"__r$depth", primitive, tail, depth + 1)
		}
	}
}

/* vim: set noexpandtab: */
