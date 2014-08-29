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

	override def generate() = {
		if (parent) {
			throw new UnsupportedOperationException;
		} else {
			val moduleFile = s"${options.moduleName}.pas"
			idl.interfaces.flatMap(interface =>
				List(
					new OutputDirectory(Paths.get(interface.name)),
					generateEntry(interface),
					generate(interface)) ++ generateLink(interface, input)
			)
		}
	}

	override def generateMakefileRules() = {
		if (parent) {
			throw new UnsupportedOperationException;
		} else {
			idl.interfaces.map(interface =>
				MakefileRule(Paths.get(interface.name, interface.name),
					List(
						Paths.get(interface.name, s"${interface.name}.pas"),
						Paths.get(interface.name, s"${idl.main.name}.pas"),
						Paths.get(interface.name, s"${interface.name}_entry.pas")),
					"/usr/bin/fpc -Tlinux -O2 -Mobjfpc -Sc -Sh -o$@ $^ > /dev/null"))
		}
	}

	override def generateTemplate(interface: Interface, input: Path) = {
		val builder = new StringBuilder
		builder ++= s"unit ${options.moduleName};\n\n"
		if (idl.main.functions.exists(_ => true)) {
			builder ++= "{\n"
			builder ++= s"    ${idl.main.name}:\n"
			builder ++= "\n"
			idl.main.functions.foreach(function =>
				builder ++= s"    ${declareFunction(function)}\n"
			)
			builder ++= "}\n\n"
		}
		builder ++= s"interface\n"
		interface.functions.foreach(function => {
			builder ++= s"\t${declareFunction(function)}\n"
		})
		builder ++= s"implementation\n\n"
		builder ++= s"uses ${idl.main.name};\n"
		interface.functions.foreach(function => {
			builder ++= s"\n${declareFunction(function)}\n"
			builder ++= "begin\n"
			builder ++= "\t// FIXME\n"
			if (function.returnType != PrimitiveType("void")) {
				builder ++= s"\t${function.name} := ${defaultValue(function.returnType)};\n"
			}
			builder ++= "end;\n"
		})
		builder ++= "\nend.\n"
		if (!options.force && Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
			throw new FileAlreadyExistsException(input.toString, null,
				"Refusing to overwrite file. Delete it or invoke with --force to override.")
		}
		OutputFile(input.toAbsolutePath, builder.mkString)
	}

	override def generateRunCommands() = {
		(if (parent) {
			throw new UnsupportedOperationException;
		} else {
			idl.interfaces
		}).map(interface =>
			ExecDescription(Array(options.root.relativize(
				options.outputDirectory
					.resolve(Paths.get(interface.name, interface.name)))
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

	private def formatLength(length: ArrayLength, prefix: String) = {
		length match {
			case constant: ConstantLength => {
				constant.value
			}
			case param: ParameterLength => {
				prefix + param.value
			}
		}
	}

	private def arrayDim(length: ArrayLength, prefix: String) = {
		s"[${formatLength(length, prefix)}]"
	}

	private def formatPrimitive(t: PrimitiveType) = {
		t.name match {
			case "long" => "Int64"
			case "int" => "LongInt"
			case "float" => "Single"
			case "double" => "Double"
			case "char" => "Char"
			case "byte" => "Byte"
		}
	}

	private def formatType(t: Type) = {
		t match {
			case arrayType: ArrayType =>
				arrayType.lengths.map(_ => "array of ").mkString +
					s"${formatPrimitive(arrayType.primitive)}"
			case primitiveType: PrimitiveType =>
				s"${formatPrimitive(primitiveType)}"
		}
	}

	private def formatParam(param: Parameter) = {
		s"${param.name}: ${formatType(param.paramType)}"
	}

	private def fieldLength(fieldType: Type, prefix: String = "") = {
		fieldType match {
			case primitiveType: PrimitiveType =>
				s"sizeof(${formatPrimitive(primitiveType)})"
			case arrayType: ArrayType =>
				s"sizeof(${formatPrimitive(arrayType.primitive)}) * " +
					arrayType.lengths.map(formatLength(_, prefix)).mkString(" * ")
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
		builder ++= s"""{ $message }

unit ${idl.main.name};

interface
	procedure __entry();
${
	idl.main.functions.map("\t" + declareFunction(_))
			.mkString("\n")
}
implementation

uses
  ${interface.name}, Classes, SysUtils;

var
	__in: TFileStream;
	__out: TFileStream;

${generateMessageLoop(List((idl.main, interface, "__out")), "__in")}

procedure __entry();
begin
	${if (options.verbose) {
		"\tWriteln(ErrOutput, #9'[" + interface.name + "] opening `" + pipeFilename(interface) + "''');\n"
	} else ""}
	__in := TFileStream.Create('${pipeFilename(interface)}', fmOpenRead);
	${if (options.verbose) {
		"\tWriteln(ErrOutput, #9'" + interface.name + "] opening `" + pipeFilename(idl.main) + "''');\n"
	} else ""}
	__out := TFileStream.Create('${pipeFilename(idl.main)}', fmOpenWrite);
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

	private def generateMessageLoop(interfaces: List[(Interface, Interface, String)], infd: String) = {
		val builder = new StringBuilder
		builder ++= s"""procedure __message_loop(__current_function: LongWord);
var
	__bytesRead: LongInt;
	__msgid: LongWord;
	__cookie: LongWord;\n"""
		for ((caller, callee, outfd) <- interfaces) {
			for (function <- callee.functions) {
				if (function.returnType != PrimitiveType("void")) {
					builder ++= s"\t${function.name}___result: ${formatType(function.returnType)};\n"
				}
				for (param <- function.params) {
					builder ++= s"\t${function.name}_${param.name}: ${formatType(param.paramType)};\n"
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
				builder ++= f"\t\t\t$$${functionIds((caller.name, callee.name, function.name))}%x:\n"
				builder ++= f"\t\t\tbegin\n"
				builder ++= s"\t\t\t\t{ ${caller.name} -> ${callee.name}.${function.name} }\n"
				if (options.verbose) {
					builder ++=
						s"""\t\t\t\tWriteln(ErrOutput, #9'[${callee.name}] calling ${function.name} begin');\n"""
				}
				for (param <- function.params) {
					builder ++= (param.paramType match {
						case array: ArrayType => {
							s"\t\t\t\tSetLength(${function.name}_${param.name}, " +
								array.lengths.map(
									formatLength(_, s"${function.name}_"))
								.mkString(", ") + ");\n" +
							s"\t\t\t\t$infd.ReadBuffer(${function.name}_${param.name}${array.lengths.map(_ => "[0]").mkString}, " +
								s"""${fieldLength(array, s"${function.name}_")});\n"""
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
					s"""${function.name}(${function.params.map(function.name + "_" + _.name).mkString(", ")});\n"""
				builder ++= s"\t\t\t\t$outfd.WriteBuffer(__msgid, sizeof(__msgid));\n"
				if (function.returnType != PrimitiveType("void")) {
					builder ++= s"\t\t\t\t$outfd.WriteBuffer(${function.name}___result, sizeof(${function.name}___result));\n"
				}
				builder ++= s"\t\t\t\t$outfd.WriteBuffer(__cookie, sizeof(__cookie));\n"
				if (options.verbose) {
					builder ++=
						s"""\t\t\t\tWriteln(ErrOutput, #9,'[${callee.name}] calling ${function.name} end');\n"""
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
				s"""\tWriteln(ErrOutput, #9'[${caller.name}] invoking ${function.name} begin');\n"""
		}
		builder ++= "\t__msgid := "
		builder ++= f"$$${functionIds((caller.name, callee.name, function.name))}%x;\n"
		builder ++= s"\t$outfd.WriteBuffer(__msgid, sizeof(__msgid));\n"
		function.params.foreach(param => {
			builder ++= (param.paramType match {
				case array: ArrayType => {
					s"\t\t\t\t$outfd.WriteBuffer(${param.name}${array.lengths.map(_ => "[0]").mkString}, " +
						s"${fieldLength(array)});\n"
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
