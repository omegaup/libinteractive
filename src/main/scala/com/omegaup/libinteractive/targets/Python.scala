// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.target

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.FileAlreadyExistsException

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
				new OutputDirectory(Paths.get(idl.main.name)),
				new OutputLink(Paths.get(idl.main.name, mainFile), input),
				generateMain,
				generateMainEntry)
		} else {
			val moduleFile = s"${options.moduleName}.py"
			generateTemplates(options.moduleName, idl.interfaces,
					idl.main.name, List(idl.main), input) ++
			idl.interfaces.flatMap(interface =>
				List(
					new OutputDirectory(Paths.get(interface.name)),
					generateLib(interface),
					generate(interface),
					generateLink(interface, input))
			)
		}
	}

	override def generateMakefileRules() = {
		List.empty[MakefileRule]
	}

	def pythonExecutable() = {
		options.os match {
			case OS.Unix => "/usr/bin/python"
			case OS.Windows => "python"
		}
	}

	override def generateRunCommands() = {
		(if (parent) {
			List(idl.main)
		} else {
			idl.interfaces
		}).map(interface =>
			ExecDescription(
				Array(pythonExecutable, relativeToRoot(
					options.outputDirectory.resolve(
						Paths.get(interface.name, s"${interface.name}_entry.py")
				)).toString)
			)
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

		val template = templates.code.python_template(this,
			options, callableInterfaces, interfacesToImplement)

		List(OutputFile(input, template.toString, false))
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

import sys
import runpy

sys.path[0] = "${relativeToRoot(
	options.outputDirectory.resolve(Paths.get(idl.main.name))).toString}"

runpy.run_module("${idl.main.name}", run_name="__main__")
"""
		OutputFile(
			Paths.get(idl.main.name, s"${idl.main.name}_entry.py"),
			builder.mkString)
	}

	private def generateMain() = {
		val main = templates.code.python_main(this, options, idl) 

		OutputFile(
			Paths.get(idl.main.name, s"${options.moduleName}.py"),
			main.toString)
	}

	private def generate(interface: Interface) = {
		val builder = new StringBuilder
		builder ++= s"""#!/usr/bin/python
# $message

import ${idl.main.name}
"""
		OutputFile(
			Paths.get(interface.name, s"${interface.name}_entry.py"),
			builder.mkString)
	}

	private def generateLib(interface: Interface) = {
		val python = templates.code.python(this, options, interface, idl.main)

		OutputFile(
			Paths.get(interface.name, s"${idl.main.name}.py"),
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
					s"for __r$depth in xrange(${formatLength(head, function)})]"
		}
	}

	def writeArray(outfd: String, name: String, primitive: PrimitiveType,
			lengths: Iterable[ArrayLength], depth: Integer = 1): String = {
		lengths match {
			case head :: Nil =>
				"\t" * depth + s"$outfd.write(struct.pack(" +
				s"'%d${structFormat(primitive).charAt(1)}' % (${head.value}), *$name))"
			case head :: tail =>
				"\t" * depth + s"for __r$depth in $name:\n" +
					writeArray(outfd, s"__r$depth", primitive, tail, depth + 1)
		}
	}
}

/* vim: set noexpandtab: */
