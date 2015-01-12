// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.idl

import scala.collection.Iterable
import scala.collection.mutable.HashMap
import scala.util.parsing.combinator.syntactical._

case class ParseException(msg: String, longString: String, line: Int, column: Int)
		extends RuntimeException(msg) {
	override def toString() = s"$longString\n$msg (line $line:$column)"
}

trait AstNode {
	def children(): Iterable[AstNode] = List[AstNode]()
}

case class IDL(main: Interface, interfaces: List[Interface])
		extends Object with AstNode {
	override def children() = List(main) ++ interfaces
	def allInterfaces() = List(main) ++ interfaces
}

case class Interface(name: String, functions: List[Function],
		attributes: List[Attribute]) extends Object with AstNode {
	override def children() = functions

	def shmSize() = attributes.flatMap(_ match {
		case size: ShmSizeAttribute => Some(size)
		case _ => None
	}).headOption.getOrElse(ShmSizeAttribute(64 * 1024)).size
}

case class Function(returnType: PrimitiveType, name: String, params: List[Parameter],
		attributes: List[Attribute]) extends Object with AstNode {
	override def children() = List(returnType) ++ params
	def noReturn() = attributes.exists(_ match {
		case NoReturnAttribute => true
		case _ => false
	})
}

abstract class Type extends Object with AstNode {
	def byteSize(): Long
}
case class PrimitiveType(name: String) extends Type {
	override def byteSize() = name match {
		case "void" => 0
		case "bool" => 1
		case "char" => 1
		case "short" => 2
		case "int" => 4
		case "float" => 4
		case "long" => 8
		case "double" => 8
	}
}
case class ArrayType(primitive: PrimitiveType, lengths: List[ArrayLength])
		extends Type {
	override def children() = List(primitive) ++ lengths
	def byteSize() = {
		var size: Long = primitive.byteSize
		lengths foreach(size *= _.range.get.max)
		size
	}
}

abstract class ArrayLength extends Object with AstNode {
	def value(): String
	def range(): Option[ValueRange]
}
case class ConstantLength(length: Long) extends ArrayLength {
	override def value() = length.toString
	override def range() = Some(ValueRange(length, length))
}
case class ParameterLength(param: Parameter) extends ArrayLength {
	override def value() = param.name
	override def range() = param.range
}

case class ValueRange(min: Long, max: Long) {
	def union(range: ValueRange) = {
		ValueRange(Math.min(min, range.min), Math.max(max, range.max))
	}
}
abstract class Expression extends Object with AstNode {
	def value(): String
	def range(): Option[ValueRange]
}
case class ConstantExpression(longValue: Long) extends Expression {
	override def value() = longValue.toString
	override def range() = Some(ValueRange(longValue, longValue))
}
case class ParameterExpression(param: Parameter) extends Expression {
	override def value() = param.name
	override def range() = param.range
}

abstract class Attribute extends Object with AstNode {}
object NoReturnAttribute extends Attribute {}
case class RangeAttribute(min: Expression, max: Expression) extends Attribute {
	override def children() = List(min, max)
	def range() = {
		if (!min.range.isEmpty && !max.range.isEmpty) {
			Some(min.range.get.union(max.range.get))
		} else {
			None
		}
	}
}
case class ShmSizeAttribute(size: Long) extends Attribute {}

case class Parameter(paramType: Type, name: String, attributes: List[Attribute])
		extends Object with AstNode {
	override def children() = List(paramType) ++ attributes

	def range(): Option[ValueRange] = {
		attributes foreach(
			_ match {
				case r: RangeAttribute => {
					return r.range
				}
				case _ => {}
			}
		)
		None
	}
}

object Validator {
	private val keywords = Set("_alignas", "_alignof", "_atomic", "_bool",
		"_complex", "_generic", "_imaginary", "_noreturn", "_static_assert",
		"_thread_local", "absolute", "abstract", "alias", "alignas", "alignof",
		"and", "and_eq", "array", "as", "asm", "assembler", "assert", "auto",
		"begin", "bitand", "bitor", "bool", "boolean", "break", "byte", "case",
		"catch", "cdecl", "char", "char16_t", "char32_t", "class", "compl",
		"const", "const_cast", "constexpr", "constructor", "continue", "cppdecl",
		"decltype", "def", "default", "defined", "del", "delete", "destructor",
		"dispose", "div", "do", "double", "downto", "dynamic_cast", "elif", "else",
		"elsif", "end", "ensure", "enum", "except", "exec", "exit", "explicit",
		"export", "exports", "extends", "extern", "external", "false", "far",
		"file", "final", "finalization", "finally", "float", "for", "forward",
		"friend", "from", "function", "global", "goto", "if", "implementation",
		"implements", "import", "in", "index", "inherited", "initialization",
		"inline", "instanceof", "int", "interface", "is", "label", "lambda",
		"library", "local", "long", "mod", "module", "mutable", "name",
		"namespace", "native", "near", "new", "next", "nil", "noexcept", "none",
		"nostackframe", "not", "not_eq", "nullptr", "object", "of", "oldfpccall",
		"on", "operator", "or", "or_eq", "out", "override", "package", "packed",
		"pascal", "pass", "print", "private", "procedure", "program", "property",
		"protected", "public", "published", "raise", "read", "record", "redo",
		"register", "reinterpret_cast", "reintroduce", "repeat", "rescue",
		"restrict", "retry", "return", "safecall", "self", "set", "shl", "short",
		"shr", "signed", "sizeof", "softfloat", "static", "static_assert",
		"static_cast", "stdcall", "strictfp", "string", "struct", "super",
		"switch", "synchronized", "template", "then", "this", "thread_local",
		"threadvar", "throw", "throws", "to", "transient", "true", "try", "type",
		"typedef", "typeid", "typename", "undef", "union", "unit", "unless",
		"unsigned", "until", "uses", "using", "var", "virtual", "void", "volatile",
		"wchar_t", "when", "while", "with", "write", "xor", "xor_eq", "yield")

	def validateName(name: String): Option[String] = {
		if (name.contains("_")) {
			return Some(s"Name `${name}' cannot contain `_'")
		} else if (keywords.contains(name.toLowerCase)) {
			return Some(s"Name `${name}' is a reserved keyword and cannot be used.")
		} else {
			None
		}
	}

	def validateExpression(len: String,
			declaredParams: scala.collection.Map[String, Parameter]): Option[String] = {
		if (!declaredParams.contains(len)) {
			return Some(s"Expression `${len}' must have been passed as a previous " +
				"parameter")
		}
		None
	}

	def validateParam(attributes: List[Attribute], paramType: Type, name: String,
			declaredParams: scala.collection.Map[String, Parameter]): Option[String] = {
		if (declaredParams.contains(name)) {
			return Some(s"Parameter `${name}' is declared more than once")
		}
		None
	}

	def validateArrayDimension(length: Expression,
			declaredParams: scala.collection.Map[String, Parameter]): Option[String] = {
		length match {
			case param: ParameterExpression => {
				if (param.param.paramType != PrimitiveType("int")) {
					return Some(s"Array index `${param.value}' must be " +
						"declared as int")
				}
			}
			case _ => {}
		}
		if (length.range.isEmpty) {
			return Some(s"Array length expression `${length.value}' must have a Range " +
					"attribute")
		} else if (length.range.get.min < 0) {
			return Some(s"Array length expression `${length.value}' must always be " +
					"greater than zero")
		}
		None
	}

	def validateArrayType(primitive: PrimitiveType, lengths: List[ArrayLength],
			declaredParams: scala.collection.Map[String, Parameter]): Option[String] = {
		for ((length, idx) <- lengths.view.zipWithIndex) {
			length match {
				case _: ParameterLength => {
					if (idx != 0) {
						return Some("Only the first index in an array may be a variable")
					}
				}
				case _ => {}
			}
		}
		None
	}
}

class Parser extends StandardTokenParsers {
	val declaredParams = HashMap.empty[String, Parameter]
	lexical.delimiters ++= List("(", ")", "[", "]", "{", "}", ",", ";")
	lexical.reserved += (
			"bool", "char", "short", "int", "long", "float", "double", "string", "void",
			"interface", "NoReturn", "Range", "ShmSize")

	def parse(input: String): IDL = {
		interfaceList(new lexical.Scanner(input)) match {
			case Success(interfaces, _) => {
				interfaces
			}
			case NoSuccess(msg, err) => {
				declaredParams.clear
				throw new ParseException(msg, err.pos.longString, err.pos.line, err.pos.column)
			}
		}
	}

	private def addNoReturn(main: Interface) = {
		// All functions in the Main interface should have the NoReturn attribute
		// by default. Add it if it's not present.
		new Interface(main.name, main.functions.map( function => {
				new Function(function.returnType, function.name, function.params,
					function.attributes ++ List(NoReturnAttribute))
		}))
	}

	private def name = ident ^?
			({ case name if Validator.validateName(name).isEmpty => name },
			 { case name => Validator.validateName(name).get })

	private def interfaceList = phrase(rep1(interface)) ^^
			{ case interfaces => new IDL(addNoReturn(interfaces.head), interfaces.tail) }
	private def interface =
			rep(interfaceAttributes) ~ ("interface" ~> name) ~
			("{" ~> rep(function) <~ "}") <~ ";" ^^
			{ case attributes ~ name ~ functions => new Interface(name, functions, attributes) }

	private def function =
			(rep(functionAttributes) ~ returnType ~ name ~
			("(" ~> repsep(param, ",") <~ ")") <~ ";") ^^
			{
				case attributes ~ returnType ~ name ~ params => {
					declaredParams.clear
					new Function(returnType, name, params, attributes)
				}
			}
	private def returnType =
			("void" ^^ { case name => new PrimitiveType(name) }) | primitive

	private def idltype = array | primitive
	private def primitive =
			("bool" | "char" | "short" | "int" | "long" | "float" | "double") ^^
			{ case name => new PrimitiveType(name) }
	private def array = primitive ~ rep1(arrayLength) ^?
			({
				case baseType ~ lengths if
						Validator.validateArrayType(baseType, lengths, declaredParams).isEmpty =>
					new ArrayType(baseType, lengths)
			},
			{
				case baseType ~ lengths =>
						Validator.validateArrayType(baseType, lengths, declaredParams).get
			})
	private def arrayLength = "[" ~> expression <~ "]" ^?
			({
				case length if
						Validator.validateArrayDimension(length, declaredParams).isEmpty => {
					length match {
						case param: ParameterExpression =>
							new ParameterLength(param.param)
						case constant: ConstantExpression =>
							new ConstantLength(constant.longValue)
					}
				}
			},
			{
				case length =>
						Validator.validateArrayDimension(length, declaredParams).get
			})


	private def expression = (
			(numericLit ^^ { case len => new ConstantExpression(len.toLong) }) |
			(name ^? ({
				case len if
						Validator.validateExpression(len, declaredParams).isEmpty =>
					new ParameterExpression(declaredParams(len))
			},
			{
				case len =>
					Validator.validateExpression(len, declaredParams).get
			})))

	private def param = rep(paramAttributes) ~ idltype ~ name ^?
			({ case attributes ~ paramType ~ name if
						Validator.validateParam(attributes, paramType,
							name, declaredParams).isEmpty => {
					val param = new Parameter(paramType, name, attributes)
					declaredParams(name) = param
					param
				}
			},
			{
				case attributes ~ paramType ~ name =>
					Validator.validateParam(attributes, paramType, name, declaredParams).get
			})

	private def interfaceAttributes = "[" ~> interfaceAttribute <~ "]"
	private def interfaceAttribute = shmSize
	private def shmSize = "ShmSize" ~> "(" ~> numericLit <~ ")" ^^
			{ case size => new ShmSizeAttribute(size.toLong) }
	private def functionAttributes = "[" ~> functionAttribute <~ "]"
	private def functionAttribute = noret
	private def noret = "NoReturn" ^^	{ case _ => NoReturnAttribute }
	private def paramAttributes = "[" ~> paramAttribute <~ "]"
	private def paramAttribute = range
	private def range =
			"Range" ~> "(" ~> expression ~ ("," ~> expression) <~ ")" ^^
			{ case minExpr ~ maxExpr => new RangeAttribute(minExpr, maxExpr) }
}

/* vim: set noexpandtab: */
