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

class Pascal(idl: IDL, options: Options, input: Path, parent: Boolean)
		extends Target(idl, options) {
	override def extension() = "pas"

	def executableExtension() = options.os match {
		case OS.Windows => ".exe"
		case _ => ""
	}

	def ldflags() = options.os match {
		case OS.Windows => "-Twin32"
		case _ => "-Tlinux"
	}

	override def generate() = {
		if (parent) {
			throw new UnsupportedOperationException;
		} else {
			val moduleFile = s"${options.moduleName}.pas"
			generateTemplates(options.moduleName, idl.interfaces,
					idl.main.name, List(idl.main), input) ++
			idl.interfaces.flatMap(interface =>
				List(
					new OutputDirectory(Paths.get(interface.name)),
					generateEntry(interface),
					generate(interface),
					generateLink(interface, input))
			)
		}
	}

	override def generateMakefileRules() = {
		if (parent) {
			throw new UnsupportedOperationException;
		} else {
			idl.interfaces.map(interface =>
				MakefileRule(Paths.get(interface.name, interface.name + executableExtension),
					List(
						Paths.get(interface.name, s"${options.moduleName}.pas"),
						Paths.get(interface.name, s"${idl.main.name}.pas"),
						Paths.get(interface.name, s"${interface.name}_entry.pas")),
					Compiler.Fpc, ldflags + " -O2 -Mobjfpc -Sc -Sh -o$@ $^" + (
						if (options.quiet) " > /dev/null" else ""
					)))
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

		val builder = new StringBuilder
		builder ++= s"unit ${moduleName};\n\n"
		builder ++= "{\n"
		builder ++= s"\tunit ${callableModuleName};\n"
		for (interface <- callableInterfaces) {
			for (function <- interface.functions) {
				builder ++= s"\t${declareFunction(function)}\n"
			}
		}
		builder ++= "}\n\n"
		builder ++= s"interface\n"
		val types = idl.allInterfaces.flatMap(_.functions.flatMap(_.params.collect(
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
		if (types.size > 0) {
			builder ++= "type\n" + types.mkString("\n") + "\n"
		}
		for (interface <- interfacesToImplement) {
			for (function <- interface.functions) {
				builder ++= s"\t${declareFunction(function)}\n"
			}
		}
		builder ++= s"implementation\n\n"
		builder ++= s"uses $callableModuleName;\n"
		for (interface <- interfacesToImplement) {
			for (function <- interface.functions) {
				builder ++= s"\n${declareFunction(function)}\n"
				builder ++= "begin\n"
				builder ++= "\t// FIXME\n"
				if (function.returnType != PrimitiveType("void")) {
					builder ++= s"\t${function.name} := ${defaultValue(function.returnType)};\n"
				}
				builder ++= "end;\n"
			}
		}
		builder ++= "\nend.\n"

		List(OutputFile(input.toAbsolutePath, builder.mkString))
	}

	override def generateRunCommands() = {
		(if (parent) {
			throw new UnsupportedOperationException;
		} else {
			idl.interfaces
		}).map(interface =>
			ExecDescription(Array(relativeToRoot(
				options.outputDirectory
					.resolve(Paths.get(interface.name, interface.name + executableExtension)))
				.toString))
		)
	}

	private def defaultValue(t: PrimitiveType) = {
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

	private def formatPrimitive(t: PrimitiveType) = {
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

	private def formatType(t: Type) = {
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

	private def formatParam(param: Parameter) = {
		s"${param.name}: ${formatType(param.paramType)}"
	}

	private def formatLength(length: ArrayLength, function: Option[Function]) = {
		length match {
			case param: ParameterLength if !function.isEmpty =>
				s"${function.get.name}_${param.value}"
			case length: ArrayLength =>
				s"(${length.value})"
		}
	}

	private def fieldLength(fieldType: Type, function: Option[Function] = None) = {
		fieldType match {
			case primitiveType: PrimitiveType =>
				s"sizeof(${formatPrimitive(primitiveType)})"
			case arrayType: ArrayType =>
				s"sizeof(${formatPrimitive(arrayType.primitive)}) * " +
					arrayType.lengths.map(formatLength(_, function)).mkString(" * ")
		}
	}

	private def declareFunction(function: Function) = {
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
			Paths.get(interface.name, s"${interface.name}_entry.pas"),
			builder.mkString)
	}

	private def generate(interface: Interface) = {
		val builder = new StringBuilder
		val types = idl.allInterfaces.flatMap(_.functions.flatMap(_.params.collect(
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
		builder ++= s"""{ $message }

unit ${idl.main.name};

interface
${ if (types.size > 0) "type\n" + types.mkString("\n") + "\n" else "" }
	procedure __entry();
${
	idl.main.functions.map("\t" + declareFunction(_))
			.mkString("\n")
}
implementation

uses
  ${options.moduleName}, Classes, SysUtils;

var
	__in: TFileStream;
	__out: TFileStream;

${generateMessageLoop(
	List((idl.main, interface, "__out")),
	options.moduleName,
	"__in")
}

procedure __entry();
begin
	${if (options.verbose) {
		"\tWriteln(ErrOutput, #9'[" + interface.name + "] opening `" +
		pipeFilename(interface, interface).replace("\\\\", "\\") + "''');\n"
	} else ""}
	__in := TFileStream.Create('${pipeFilename(interface, interface)
		.replace("\\\\", "\\")}',
			fmOpenRead or fmShareDenyNone);
	${if (options.verbose) {
		"\tWriteln(ErrOutput, #9'" + interface.name + "] opening `" +
		pipeFilename(idl.main, interface).replace("\\\\", "\\") + "''');\n"
	} else ""}
	__out := TFileStream.Create('${pipeFilename(idl.main, interface)
		.replace("\\\\", "\\")}',
			fmOpenWrite or fmShareDenyNone);
	__message_loop($$FFFFFFFF);
end;

"""
		for (function <- idl.main.functions) {
			builder ++= generateShim(function, idl.main, interface, "__out", "__in")
		}

		builder ++= "end.\n"
		OutputFile(
			Paths.get(interface.name, s"${idl.main.name}.pas"),
			builder.mkString)
	}

	private def generateMessageLoop(interfaces: List[(Interface, Interface, String)],
			calleeModule: String, infd: String) = {
		val builder = new StringBuilder
		builder ++= s"""procedure __message_loop(__current_function: LongWord);
var
	__bytesRead: LongInt;
	__msgid: LongWord;
	__cookie: LongWord;\n"""
		for ((caller, callee, outfd) <- interfaces) {
			for (function <- callee.functions) {
				if (function.returnType != PrimitiveType("void")) {
					builder ++=
						s"\t${function.name}___result: ${formatType(function.returnType)};\n"
				}
				for (param <- function.params) {
					builder ++=
						s"\t${function.name}_${param.name}: ${formatType(param.paramType)};\n"
				}
			}
		}
	builder ++= s"""begin
	while true do
	begin
		__bytesRead := $infd.Read(__msgid, sizeof(__msgid));
		if (__bytesRead <> sizeof(__msgid)) then
			break;
		if (__msgid = __current_function) then
			exit;
		case __msgid of\n"""
		for ((caller, callee, outfd) <- interfaces) {
			for (function <- callee.functions) {
				builder ++= f"\t\t\t$$${functionIds((caller.name, callee.name,
					function.name))}%x:\n"
				builder ++= f"\t\t\tbegin\n"
				builder ++= s"\t\t\t\t{ ${caller.name} -> ${callee.name}.${function.name} }\n"
				if (options.verbose) {
					builder ++=
						s"""\t\t\t\tWriteln(ErrOutput, #9'[${callee.name}] """ +
						s"""calling ${function.name} begin');\n"""
				}
				for (param <- function.params) {
					builder ++= (param.paramType match {
						case array: ArrayType => {
							(array.lengths.head match {
								case len: ParameterLength =>
									s"\t\t\t\tSetLength(${function.name}_${param.name}, " +
										s"${function.name}_${len.value});\n"
								case _ => ""
							}) +
							s"\t\t\t\t$infd.ReadBuffer(${function.name}_${param.name}" +
								s"${array.lengths.map(_ => "[0]").mkString}, " +
								s"""${fieldLength(array, Some(function))});\n"""
						}
						case primitive: PrimitiveType => {
							s"\t\t\t\t$infd.ReadBuffer(${function.name}_${param.name}, " +
								s"sizeof(${function.name}_${param.name}));\n"
						}
					})
				}
				builder ++= s"\t\t\t\t$infd.ReadBuffer(__cookie, sizeof(__cookie));\n"
				builder ++= (if (function.returnType == PrimitiveType("void")) {
					"\t\t\t\t"
				} else {
					s"\t\t\t\t${function.name}___result := "
				})
				builder ++=
					s"""${calleeModule}.${function.name}(${function.params.map(
						function.name + "_" + _.name).mkString(", ")});\n"""
				builder ++= s"\t\t\t\t$outfd.WriteBuffer(__msgid, sizeof(__msgid));\n"
				if (function.returnType != PrimitiveType("void")) {
					builder ++= s"\t\t\t\t$outfd.WriteBuffer(${function.name}___result, " +
						s"sizeof(${function.name}___result));\n"
				}
				builder ++= s"\t\t\t\t$outfd.WriteBuffer(__cookie, sizeof(__cookie));\n"
				if (options.verbose) {
					builder ++=
						s"""\t\t\t\tWriteln(ErrOutput, #9,'[${callee.name}] """ +
						s"""calling ${function.name} end');\n"""
				}
				builder ++= "\t\t\tend;\n"
			}
		}
		builder ++= """			else begin
				Writeln(ErrOutput, 'Unknown message id 0x', IntToHex(__msgid, 8));
				halt(1);
			end;
		end;
	end;
	if (__current_function <> $FFFFFFFF) then
	begin
		Writeln(ErrOutput, 'Confused about exiting');
		Halt(1);
	end;
end;
"""
		builder
	}

	private def generateShim(function: Function, callee: Interface, caller: Interface,
			outfd: String, infd: String) = {
		val builder = new StringBuilder
		builder ++= declareFunction(function) + "\n"
		builder ++= "var\n"
		builder ++= "\t__msgid: LongWord;\n"
		builder ++= "\t__cookie: LongWord;\n"
		builder ++= "\t__cookie_result: LongWord;\n"
		if (function.returnType != PrimitiveType("void")) {
			builder ++= s"\t__result: ${formatType(function.returnType)};\n"
		}
		builder ++= "begin\n"
		if (options.verbose) {
			builder ++=
				s"""\tWriteln(ErrOutput, #9'[${caller.name}] """ +
				s"""invoking ${function.name} begin');\n"""
		}
		builder ++= "\t__msgid := "
		builder ++= f"$$${functionIds((caller.name, callee.name, function.name))}%x;\n"
		builder ++= s"\t$outfd.WriteBuffer(__msgid, sizeof(__msgid));\n"
		function.params.foreach(param => {
			builder ++= (param.paramType match {
				case array: ArrayType => {
					s"\t\t\t\t$outfd.WriteBuffer(${param.name}${array.lengths.map(
						_ => "[0]").mkString}, ${fieldLength(array)});\n"
				}
				case primitive: PrimitiveType => {
					s"\t\t\t\t$outfd.WriteBuffer(${param.name}, " +
						s"sizeof(${param.name}));\n"
				}
			})
		})
		builder ++= f"\t__cookie := $$${rand.nextInt}%x;\n"
		builder ++= s"\t$outfd.WriteBuffer(__cookie, sizeof(__cookie));\n"
		builder ++= "\t__message_loop(__msgid);\n"
		if (function.returnType != PrimitiveType("void")) {
			builder ++= s"\t$infd.ReadBuffer(__result, sizeof(__result));\n"
		}
		builder ++= s"\t$infd.ReadBuffer(__cookie_result, sizeof(__cookie_result));\n"

		builder ++= "\tif (__cookie <> __cookie_result) then\n"
		builder ++= "\tbegin\n"
		builder ++= "\t\tWriteln(ErrOutput, 'invalid cookie');\n"
		builder ++= "\t\tHalt(1);\n"
		builder ++= "\tend;\n"

		if (options.verbose) {
			builder ++=
				s"\tWriteln(ErrOutput, #9'[${caller.name}] invoking ${function.name} end');\n"
		}

		if (function.returnType != PrimitiveType("void")) {
			builder ++= s"\t${function.name} := __result;\n"
		}
		builder ++= "end;\n"

		builder
	}
}

/* vim: set noexpandtab: */
