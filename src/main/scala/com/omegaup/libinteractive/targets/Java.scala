package com.omegaup.libinteractive.target

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.FileAlreadyExistsException

import scala.collection.mutable.StringBuilder

import com.omegaup.libinteractive.idl._

class Java(idl: IDL, options: Options, input: Path, parent: Boolean)
		extends Target(idl, options) {
	override def extension() = "java"

	override def generate() = {
		if (parent) {
			val mainFile = s"${idl.main.name}.java"
			List(
				new OutputDirectory(Paths.get(idl.main.name)),
				new OutputLink(Paths.get(idl.main.name, mainFile), input),
				generateMainFile)
		} else {
			val moduleFile = s"${options.moduleName}.java"
			idl.interfaces.flatMap(interface =>
				List(
					new OutputDirectory(Paths.get(interface.name)),
					generate(interface)) ++ generateLink(interface, input)
			)
		}
	}

	override def generateMakefileRules() = {
		if (parent) {
			List(MakefileRule(Paths.get(idl.main.name, s"${idl.main.name}.class"),
				List(
					Paths.get(idl.main.name, s"${idl.main.name}.java"),
					Paths.get(idl.main.name, s"${idl.main.name}_entry.java")),
				"/usr/bin/javac $^"))
		} else {
			idl.interfaces.flatMap(interface =>
				List(
					MakefileRule(Paths.get(interface.name, s"${interface.name}_entry.class"),
						List(
							Paths.get(interface.name, s"${interface.name}.java"),
							Paths.get(interface.name, s"${interface.name}_entry.java")),
						"/usr/bin/javac $^")
				)
			)
		}
	}

	override def generateRunCommands() = {
		(if (parent) {
			List(idl.main)
		} else {
			idl.interfaces
		}).map(interface =>
			ExecDescription(Array("/usr/bin/java", "-cp",
				options.root.relativize(
					options.outputDirectory.resolve(interface.name)
				).toString,
				s"${interface.name}_entry"))
		)
	}

	override def generateTemplate(interface: Interface, input: Path) = {
		val builder = new StringBuilder
		if (idl.main.functions.exists(_ => true)) {
			builder ++= s"// ${idl.main.name}:\n"
			builder ++= "//\n"
			idl.main.functions.foreach(function =>
				builder ++= s"//\t${declareFunction(function)}\n"
			)
			builder ++= "\n"
		}
		builder ++= s"public class ${options.moduleName} {\n"
		interface.functions.foreach(function => {
			builder ++= s"\n\tpublic static ${declareFunction(function)} {\n"
			builder ++= "\t\t// FIXME\n"
			if (function.returnType != PrimitiveType("void")) {
				builder ++= s"\t\treturn ${defaultValue(function.returnType)};\n"
			}
			builder ++= "\t}\n"
		})
		builder ++= "\n}\n"
		if (!options.force && Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
			throw new FileAlreadyExistsException(input.toString, null,
				"Refusing to overwrite file. Delete it or invoke with --force to override.")
		}
		OutputFile(input.toAbsolutePath, builder.mkString)
	}

	private def arrayDim(length: ArrayLength) = s"[${length.value}]"

	private def defaultValue(t: PrimitiveType) = {
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

	private def formatPrimitive(t: PrimitiveType) = {
		t.name
	}

	private def formatType(t: Type) = {
		t match {
			case arrayType: ArrayType =>
				s"${formatPrimitive(arrayType.primitive)}" +
					arrayType.lengths.map(_ => "[]").mkString
			case primitiveType: PrimitiveType =>
				s"${formatPrimitive(primitiveType)}"
		}
	}

	private def formatParam(param: Parameter) = {
		s"${formatType(param.paramType)} ${param.name}"
	}

	private def declareFunction(function: Function) = {
		s"${formatPrimitive(function.returnType)} ${function.name}(" +
			function.params.map(formatParam).mkString(", ") + ")"
	}

	private def arrayLength(arrayType: ArrayType) = {
			arrayType.lengths.map(_.value).mkString(", ")
	}

	private def declareVar(param: Parameter) = {
		s"${formatType(param.paramType)} ${param.name}"
	}

	private def writePrimitive(primitive: PrimitiveType) = {
		primitive match {
			case PrimitiveType("int") => "writeInt"
			case PrimitiveType("long") => "writeLong"
			case PrimitiveType("char") => "writeChar"
			case PrimitiveType("float") => "writeFloat"
			case PrimitiveType("double") => "writeDouble"
			case PrimitiveType("bool") => "writeBool"
		}
	}

	private def readPrimitive(primitive: PrimitiveType) = {
		primitive match {
			case PrimitiveType("int") => "readInt"
			case PrimitiveType("long") => "readLong"
			case PrimitiveType("char") => "readChar"
			case PrimitiveType("float") => "readFloat"
			case PrimitiveType("double") => "readDouble"
			case PrimitiveType("bool") => "readBool"
		}
	}

	private def readArray(infd: String, param: Parameter, array: ArrayType,
			startingLevel: Int) = {
		var level = startingLevel
		val builder = new StringBuilder
		for (expr <- array.lengths) {
			builder ++= "\t" * level +
					s"for (int __i$level = 0; __i$level < ${expr.value}; __i$level++) {\n"
			level += 1
		}
		builder ++= "\t" * level +
			s"""${param.name}${(startingLevel until level).map(
				idx => s"[__i$idx]").mkString} = """ +
			s"$infd.${readPrimitive(array.primitive)}();\n"
		for (expr <- array.lengths) {
			level -= 1
			builder ++= "\t" * level + "}\n"
		}
		builder
	}

	private def writeArray(outfd: String, param: Parameter, array: ArrayType,
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
			s"""${param.name}${(startingLevel until level).map(
				idx => s"[__i$idx]").mkString});\n"""
		for (expr <- array.lengths) {
			level -= 1
			builder ++= "\t" * level + "}\n"
		}
		builder.toString
	}

	private def generate(interface: Interface) = {
		val builder = new StringBuilder
		builder ++= s"""/* $message */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

$generateDataStreams

public class ${interface.name}_entry {
	static LEDataInputStream __in = null;
	static LEDataOutputStream __out = null;

${generateMessageLoop(List((idl.main, interface, "__out")), "__in")}

	public static void main(String[] args) throws IOException {
		${if (options.verbose) {
			"System.err.printf(\"\\t[" + interface.name + "] opening `" +
				pipeFilename(interface) + "'\\n\");\n"
		} else ""}
		try (LEDataInputStream fin =
				new LEDataInputStream("${pipeFilename(interface)}")) {
			${if (options.verbose) {
				"System.err.printf(\"\\t[" + interface.name + "] opening `" +
					pipeFilename(idl.main) + "'\\n\");\n"
			} else ""}
			try (LEDataOutputStream fout =
					new LEDataOutputStream("${pipeFilename(idl.main)}")) {
				__in = fin;
				__out = fout;
				__message_loop(-1);
			}
		}
	}
"""
		builder ++= "}\n\n"

		builder ++= s"class ${idl.main.name} {\n"
		for (function <- idl.main.functions) {
			builder ++= generateShim(function, idl.main, interface,
				s"${interface.name}_entry.__out",
				s"${interface.name}_entry.__in", false)
		}
		builder ++= "}\n"

		OutputFile(
			Paths.get(interface.name, s"${interface.name}_entry.java"),
			builder.mkString)
	}

	private def generateMainFile() = {
		val builder = new StringBuilder
		builder ++= s"""/* $message */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

$generateDataStreams

public class ${idl.main.name}_entry {
	static long __elapsed_time = 0;
	static LEDataInputStream ${pipeName(idl.main)} = null;
	static LEDataOutputStream ${idl.interfaces.map(pipeName).mkString(", ")};

"""
		builder ++= generateMessageLoop(
			idl.interfaces.map{
				interface => (interface, idl.main, pipeName(interface))
			},
			pipeName(idl.main)
		)

		builder ++= "\tpublic static void main(String[] args) throws IOException {\n"
		var indentLevel = 2
		idl.interfaces.foreach(interface => {
			if (options.verbose) {
				builder ++= "\t" * indentLevel + "System.err.println(" +
						s""" "\\t[${idl.main.name}] opening `${pipeFilename(interface)}'");\n"""
			}
			builder ++= "\t" * indentLevel +
					s"""try (LEDataOutputStream __${pipeName(interface)} = """ +
					s"""new LEDataOutputStream("${pipeFilename(interface)}")) {\n"""
			indentLevel += 1
			builder ++= "\t" * indentLevel +
				s"${pipeName(interface)} = __${pipeName(interface)};\n"
		})
		if (options.verbose) {
			builder ++= "\t" * indentLevel + "System.err.println(" +
					s""" "\\t[${idl.main.name}] opening `${pipeFilename(idl.main)}'");\n"""
		}
		builder ++= "\t" * indentLevel +
				s"""try (LEDataInputStream __${pipeName(idl.main)} = """ +
				s"""new LEDataInputStream("${pipeFilename(idl.main)}")) {\n"""
		indentLevel += 1
		builder ++= "\t" * indentLevel +
			s"${pipeName(idl.main)} = __${pipeName(idl.main)};\n"
		builder ++= "\t" * indentLevel +
				s"${idl.main.name}.main(args);\n"
		while (indentLevel > 2) {
			indentLevel -= 1
			builder ++= "\t" * indentLevel + "}\n"
		}
		builder ++= "\t}\n"
		builder ++= "}\n\n"
		
		idl.interfaces.foreach(interface => {
			builder ++= s"class ${interface.name} {\n"
			interface.functions.foreach(
				builder ++= generateShim(_, interface, idl.main,
					s"${idl.main.name}_entry.${pipeName(interface)}",
					s"${idl.main.name}_entry.${pipeName(idl.main)}", true)
			)
			builder ++= s"}\n\n"
		})
		
		OutputFile(
			Paths.get(idl.main.name, s"${idl.main.name}_entry.java"),
			builder.mkString)
	}

	private def generateMessageLoop(interfaces: List[(Interface, Interface, String)],
			infd: String) = {
		val builder = new StringBuilder
		builder ++=
			s"""	static void __message_loop(int __current_function) throws IOException {
		int __msgid;
		while (true) {
			try {
				__msgid = $infd.readInt();
			} catch (EOFException e) {
				break;
			}
			if (__msgid == __current_function) return;
			switch (__msgid) {\n"""
		for ((caller, callee, outfd) <- interfaces) {
			for (function <- callee.functions) {
				builder ++= f"\t\t\t\tcase 0x${functionIds((caller.name, callee.name,
					function.name))}%x: {\n"
				builder ++= s"\t\t\t\t\t// ${caller.name} -> ${callee.name}.${function.name}\n"
				if (options.verbose) {
					builder ++=
						s"""\t\t\t\t\tSystem.err.printf("\\t[${callee.name}] calling """ +
						s"""${function.name} begin\\n");\n"""
				}
				for (param <- function.params) {
					builder ++= (param.paramType match {
						case array: ArrayType => {
							s"\t\t\t\t\t${declareVar(param)} = new ${formatType(array.primitive)}" +
								s"""${array.lengths.map(arrayDim).mkString("")};\n""" +
								readArray(infd, param, array, 5)
						}
						case primitive: PrimitiveType => {
							s"\t\t\t\t\t${declareVar(param)} = " +
								s"$infd.${readPrimitive(primitive)}();\n"
						}
					})
				}
				builder ++= s"\t\t\t\t\tint __cookie = $infd.readInt();\n"
				builder ++= (if (function.returnType == PrimitiveType("void")) {
					"\t\t\t\t\t"
				} else {
					s"\t\t\t\t\t${formatType(function.returnType)} __result = "
				})
				builder ++=
					s"""${callee.name}.${function.name}(${function.params.map(
						_.name).mkString(", ")});\n"""
				builder ++= s"\t\t\t\t\t$outfd.writeInt(__msgid);\n"
				if (function.returnType != PrimitiveType("void")) {
					builder ++= s"\t\t\t\t\t$outfd.${writePrimitive(function.returnType)}" +
						"(__result);\n"
				}
				builder ++= s"\t\t\t\t\t$outfd.writeInt(__cookie);\n"
				builder ++= s"\t\t\t\t\t$outfd.flush();\n"
				if (options.verbose) {
					builder ++=
						s"""\t\t\t\t\tSystem.err.printf("\\t[${callee.name}] calling """ +
						s"""${function.name} end\\n");\n"""
				}
				builder ++= "\t\t\t\t\tbreak;\n"
				builder ++= "\t\t\t\t}\n"
			}
		}
		builder ++= """				default: {
					System.err.printf("Unknown message id 0x%x\n", __msgid);
					System.exit(1);
				}
			}
		}
		if (__current_function != -1) {
			System.err.printf("Confused about exiting\n");
			System.exit(1);
		}
	}
"""
		builder
	}

	private def generateShim(function: Function, callee: Interface, caller: Interface,
			outfd: String, infd: String, generateTiming: Boolean) = {
		val builder = new StringBuilder
		builder ++= s"\tpublic static ${declareFunction(function)} {\n"
		builder ++= "\t\ttry {\n"
		if (options.verbose) {
			builder ++=
				s"""\t\t\tSystem.err.printf("\\t[${caller.name}] """ +
				s"""invoking ${function.name} begin\\n");\n"""
		}
		builder ++= "\t\t\tfinal int __msgid = "
		builder ++= f"0x${functionIds((caller.name, callee.name, function.name))}%x;\n"
		builder ++= s"\t\t\t$outfd.writeInt(__msgid);\n"
		function.params.foreach(param => {
			builder ++= (param.paramType match {
				case primitive: PrimitiveType =>
					s"\t\t\t$outfd.${writePrimitive(primitive)}(${param.name});\n"
				case array: ArrayType =>
					writeArray(outfd, param, array, 3)
			})
		})
		if (generateTiming) {
			builder ++=
				"\t\t\tlong __t0, __t1;\n\t\t\t__t0 = System.nanoTime();\n"
		}
		builder ++= f"\t\t\tint __cookie = 0x${rand.nextInt}%x;\n"
		builder ++= s"\t\t\t$outfd.writeInt(__cookie);\n"
		builder ++= s"\t\t\t$outfd.flush();\n"
		builder ++= s"\t\t\t${caller.name}_entry.__message_loop(__msgid);\n"
		if (function.returnType != PrimitiveType("void")) {
			builder ++= s"\t\t\t${formatType(function.returnType)} __ans = "
			builder ++= s"$infd.${readPrimitive(function.returnType)}();\n"
		}
		builder ++= s"\t\t\tint __cookie_result = $infd.readInt();\n"
		if (generateTiming) {
			builder ++= "\t\t\t__t1 = System.nanoTime();\n"
			builder ++= s"\t\t\t${caller.name}_entry.__elapsed_time += __t1 - __t0;\n"
		}

		builder ++= "\t\t\tif (__cookie != __cookie_result) {\n"
		builder ++= "\t\t\t\tSystem.err.printf(\"invalid cookie\\n\");\n"
		builder ++= "\t\t\t\tSystem.exit(1);\n"
		builder ++= "\t\t\t}\n"

		if (options.verbose) {
			builder ++=
				s"""\t\t\tSystem.err.printf("\\t[${caller.name}] """ +
				s"""invoking ${function.name} end\\n");\n"""
		}
		if (function.returnType != PrimitiveType("void")) {
			builder ++= "\t\t\treturn __ans;\n"
		}

		builder ++= "\t\t} catch (IOException e) {\n"
		builder ++= "\t\t\tSystem.err.println(e);\n"
		builder ++= "\t\t\te.printStackTrace();\n"
		builder ++= "\t\t\tSystem.exit(1);\n"
		builder ++= "\t\t\tthrow new RuntimeException(); // Needed to compile.\n"
		builder ++= "\t\t}\n"
		builder ++= "\t}\n"

		builder
	}

	/**
	 * Generates little-endian streams that are compatible with the C implementation.
	 * This makes libinteractive work in 95% of the CPUs out there.
	 */
	private def generateDataStreams() = {
		"""class LEDataInputStream extends FilterInputStream {
	public LEDataInputStream(String path) throws FileNotFoundException {
		super(new BufferedInputStream(new FileInputStream(path)));
	}

	public boolean readBool() throws IOException {
		int c = in.read();
		if (c == -1) {
			throw new EOFException();
		}
		return c != 0;
	}

	public char readChar() throws IOException {
		int c = in.read();
		if (c == -1) {
			throw new EOFException();
		}
		return (char)c;
	}

	public float readFloat() throws IOException {
		return Float.intBitsToFloat(readInt());
	}

	public double readDouble() throws IOException {
		return Double.longBitsToDouble(readLong());
	}

	public long readLong() throws IOException {
		byte[] b = new byte[8];
		if (in.read(b) != 8) {
			throw new EOFException();
		}
		return ((b[7] & 0xffL) << 56L) | ((b[6] & 0xffL) << 48L) |
				((b[5] & 0xffL) << 40L) | ((b[4] & 0xffL) << 32L) |
				((b[3] & 0xffL) << 24L) | ((b[2] & 0xffL) << 16L) |
				((b[1] & 0xffL) << 8L) | (b[0] & 0xffL);
	}

	public int readInt() throws IOException {
		byte[] b = new byte[4];
		if (in.read(b) != 4) {
			throw new EOFException();
		}
		return ((b[3] & 0xff) << 24) | ((b[2] & 0xff) << 16) |
			((b[1] & 0xff) << 8) | (b[0] & 0xff);
	}
}

class LEDataOutputStream extends FilterOutputStream {
	public LEDataOutputStream(String path) throws FileNotFoundException {
		super(new BufferedOutputStream(new FileOutputStream(path)));
	}

	public void writeInt(int x) throws IOException {
		out.write((x >>> 0) & 0xFF);
		out.write((x >>> 8) & 0xFF);
		out.write((x >>> 16) & 0xFF);
		out.write((x >>> 24) & 0xFF);
	}

	public void writeLong(long x) throws IOException {
		out.write((int)((x >>> 0) & 0xFF));
		out.write((int)((x >>> 8) & 0xFF));
		out.write((int)((x >>> 16) & 0xFF));
		out.write((int)((x >>> 24) & 0xFF));
		out.write((int)((x >>> 32) & 0xFF));
		out.write((int)((x >>> 40) & 0xFF));
		out.write((int)((x >>> 48) & 0xFF));
		out.write((int)((x >>> 52) & 0xFF));
	}

	public void writeChar(char c) throws IOException {
		out.write((int)c);
	}

	public void writeFloat(float f) throws IOException {
		writeInt(Float.floatToIntBits(f));
	}

	public void writeDouble(double d) throws IOException {
		writeLong(Double.doubleToLongBits(d));
	}

	public void writeBool(boolean b) throws IOException {
		out.write(b ? 1 : 0);
	}

}
"""
	}
}

/* vim: set noexpandtab: */
