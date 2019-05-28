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

class Pascal(idl: IDL, options: Options, input: Path, parent: Boolean)
		extends Target(idl, options) {
	override def extension() = "pas"

	def executableExtension() = options.os match {
		case OS.Windows => ".exe"
		case _ => ""
	}

	def ldflags() = options.os match {
		case OS.Windows => List("-Twin32")
		case _ => List("-Tlinux")
	}

	override def generate() = {
		if (parent) {
			throw new UnsupportedOperationException;
		} else {
			generateTemplates(input) ++
			idl.interfaces.flatMap(generateInterface)
		}
	}

	override def generateInterface(interface: Interface) = {
		List(
			new OutputDirectory(options.resolve(interface.name)),
			generateEntry(interface),
			generate(interface),
			generateLink(interface, input))
	}

	override def generateMakefileRules() = {
		if (parent) {
			throw new UnsupportedOperationException;
		} else {
			idl.interfaces.flatMap(generateMakefileRules)
		}
	}

	override def generateMakefileRules(interface: Interface) = {
		List(
			MakefileRule(
				target = List(
					options.relativeToRoot(interface.name, interface.name + executableExtension)
				),
				requisites = List(
					options.relativeToRoot(interface.name, s"${options.moduleName}.pas"),
					options.relativeToRoot(interface.name, s"${idl.main.name}.pas"),
					options.relativeToRoot(interface.name, s"${interface.name}_entry.pas")
				),
				compiler = Compiler.Fpc,
				params = ldflags ++ List("-O2", "-Mobjfpc", "-Sc", "-Sh", "-o$@", "$^") ++ (
					if (options.quiet) List(">", "/dev/null") else List()
				)
			),
			MakefileRule(
				target = List(
					options.relativeToRoot(interface.name, interface.name + "_debug" + executableExtension)
				),
				requisites = List(
					options.relativeToRoot(interface.name, s"${options.moduleName}.pas"),
					options.relativeToRoot(interface.name, s"${idl.main.name}.pas"),
					options.relativeToRoot(interface.name, s"${interface.name}_entry.pas")
				),
				compiler = Compiler.Fpc,
				params = ldflags ++ List("-g", "-Mobjfpc", "-Sc", "-Sh", "-o$@", "$^") ++ (
					if (options.quiet) List(">", "/dev/null") else List()
				),
				debug = true
			)
		)
	}

	override def generateTemplateSource(): String = {
		templates.code.pascal_template(this, options, options.moduleName,
			List(idl.main), idl.interfaces, idl.main.name).toString
	}

	private def gdbserverPath = {
		options.os match {
			case OS.Unix => "/usr/bin/gdbserver"
			case OS.Windows => "gdbserver.exe"
		}
	}

	private def runCommand(interface: Interface, suffix: String = "") = {
		options.relativeToRoot(interface.name,
			interface.name + suffix + executableExtension).toString
	}

	override def generateRunCommands() = {
		if (parent) {
			throw new UnsupportedOperationException;
		} else {
			List(idl.interfaces.head).map(
				interface => ExecDescription(
					args = Array(runCommand(interface)),
					debug_args = Some(
						Array(gdbserverPath, "127.0.0.1:8042", runCommand(interface, "_debug"))
					)
				)
			) ++
			idl.interfaces.tail.map(generateRunCommand)
		}
	}

	override def generateRunCommand(interface: Interface) = {
		ExecDescription(Array(runCommand(interface)))
	}

	private def generateEntry(interface: Interface) = {
		val builder = new StringBuilder
		builder ++= s"""{ $message }

program ${interface.name};

uses Main;

begin
  __entry();
end.
"""
		OutputFile(
			options.resolve(interface.name, s"${interface.name}_entry.pas"),
			builder.mkString)
	}

	private def generate(interface: Interface) = {
		val pascal = templates.code.pascal(this, options, interface, idl.main)

		OutputFile(
			options.resolve(interface.name, s"${idl.main.name}.pas"),
			pascal.toString)
	}

	def defaultValue(t: PrimitiveType) = {
		t.name match {
			case "bool" => "False"
			case "char" => "#0"
			case "short" => "0"
			case "int" => "0"
			case "float" => "0.0"
			case "long" => "0"
			case "double" => "0.0"
		}
	}

	def formatPrimitive(t: PrimitiveType) = {
		t.name match {
			case "bool" => "Boolean"
			case "short" => "SmallInt"
			case "long" => "Int64"
			case "int" => "LongInt"
			case "float" => "Single"
			case "double" => "Double"
			case "char" => "Char"
		}
	}

	def formatType(t: Type) = {
		t match {
			case arrayType: ArrayType =>
				"T_" + arrayType.lengths.map(
					_ match {
						case constant: ConstantLength => constant.value
						case parameter: ParameterLength => "Array"
					}
				).mkString("_") +
					s"_${formatPrimitive(arrayType.primitive)}"
			case primitiveType: PrimitiveType =>
				s"${formatPrimitive(primitiveType)}"
		}
	}

	def formatParam(param: Parameter) = {
		s"${param.name}: ${formatType(param.paramType)}"
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
		(if (function.returnType == PrimitiveType("void")) {
			"procedure"
		} else {
			"function"
		}) +
		s" ${function.name}(" +
			function.params.map(formatParam).mkString("; ") + ")" +
		(if (function.returnType == PrimitiveType("void")) {
			""
		} else {
			s": ${formatPrimitive(function.returnType)}"
		}) + ";"
	}


	def arrayTypes() = {
		idl.allInterfaces.flatMap(_.functions.flatMap(_.params.collect(
			_.paramType match {
				case array: ArrayType => {
					s"\t${formatType(array)} = " + array.lengths.map(
						_ match {
							case constant: ConstantLength =>
								s"array [0..${constant.length - 1}] of "
							case param: ParameterLength => "array of "
						}
					).mkString + formatType(array.primitive) + ";"
				}
			}
		))).toSet
	}
}

/* vim: set noexpandtab: */
