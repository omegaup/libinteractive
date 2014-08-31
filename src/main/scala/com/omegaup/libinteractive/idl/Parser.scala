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

case class Interface(name: String, functions: List[Function])
		extends Object with AstNode {
	override def children() = functions
}

case class Function(returnType: PrimitiveType, name: String, params: List[Parameter])
		extends Object with AstNode {
	override def children() = List(returnType) ++ params
}

abstract class Type extends Object with AstNode {}
case class PrimitiveType(name: String) extends Type {}
case class ArrayType(primitive: PrimitiveType, lengths: List[ArrayLength])
		extends Type {
	override def children() = List(primitive) ++ lengths
	def byteSize() = {
		var size: Long = 1
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
		if (name.contains("_")) {
			return Some(s"Parameter `${name}' cannot contain `_'")
		} else if (declaredParams.contains(name)) {
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
			"interface", "Range")

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

	private def interfaceList = phrase(rep1(interface)) ^^
			{ case interfaces => new IDL(interfaces.head, interfaces.tail) }
	private def interface =
			"interface" ~> ident ~ ("{" ~> rep(function) <~ "}") <~ ";" ^^
			{ case name ~ functions => new Interface(name, functions) }

	private def function =
			returnType ~ ident ~ ("(" ~> repsep(param, ",") <~ ")") <~ ";" ^^
			{
				case returnType ~ name ~ params => {
					declaredParams.clear
					new Function(returnType, name, params)
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
			(ident ^? ({
				case len if
						Validator.validateExpression(len, declaredParams).isEmpty =>
					new ParameterExpression(declaredParams(len))
			},
			{
				case len =>
					Validator.validateExpression(len, declaredParams).get
			})))

	private def param = rep(paramAttributes) ~ idltype ~ ident ^?
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

	private def paramAttributes = range
	private def range =
			"[" ~> "Range" ~> "(" ~> expression ~ ("," ~> expression) <~ ")" <~ "]" ^^
			{ case minExpr ~ maxExpr => new RangeAttribute(minExpr, maxExpr) }
}

/* vim: set noexpandtab: */
