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
case class ArrayType(primitive: PrimitiveType, lengths: List[ArrayLength]) extends Type {
	override def children() = List(primitive) ++ lengths
}

abstract class ArrayLength extends Object with AstNode {
	def value(): String
}
case class ParameterLength(param: Parameter) extends ArrayLength {
	override def value() = param.name
}
case class ConstantLength(length: Int) extends ArrayLength {
	override def value() = length.toString
}

abstract class Expression extends Object with AstNode {
	def value(): String
}
case class IntExpression(intValue: Int) extends Expression {
	override def value() = intValue.toString
}
case class VariableExpression(nameValue: String) extends Expression {
	override def value() = nameValue
}

abstract class Attribute extends Object with AstNode {}
case class RangeAttribute(min: Expression, max: Expression) extends Attribute {
	override def children() = List(min, max)
}
case class Parameter(paramType: Type, name: String, attributes: List[Attribute])
		extends Object with AstNode {
	override def children() = List(paramType) ++ attributes
}

object SemanticValidator {
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
			case variable: VariableExpression => {
				if (!declaredParams.contains(variable.value)) {
					return Some(s"Array index `${variable.value}' must have " +
						s"been passed as a previous parameter")
				}
				if (declaredParams(variable.value).paramType != PrimitiveType("int")) {
					return Some(s"Array index `${variable.value}' must be " +
						"declared as int")
				}
			}
			case _ => {}
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
			"bool", "int", "short", "float", "char", "string", "long", "void",
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
			primitive ~ ident ~ ("(" ~> repsep(param, ",") <~ ")") <~ ";" ^^
			{
				case returnType ~ name ~ params => {
					declaredParams.clear
					new Function(returnType, name, params)
				}
			}

	private def idltype = array | primitive
	private def primitive =
			("bool" | "int" | "short" | "float" | "char" | "string" | "long" |
			"void") ^^
			{ case name => new PrimitiveType(name) }
	private def array = primitive ~ rep1(arrayLength) ^?
			({
				case baseType ~ lengths if
						SemanticValidator.validateArrayType(baseType, lengths, declaredParams).isEmpty =>
					new ArrayType(baseType, lengths)
			},
			{
				case baseType ~ lengths =>
						SemanticValidator.validateArrayType(baseType, lengths, declaredParams).get
			})
	private def arrayLength = "[" ~> expression <~ "]" ^?
			({
				case length if
						SemanticValidator.validateArrayDimension(length, declaredParams).isEmpty => {
						length match {
							case variable: VariableExpression =>
								new ParameterLength(declaredParams(length.value))
							case literal: IntExpression =>
								new ConstantLength(literal.intValue)
						}
					}
			},
			{
				case length =>
						SemanticValidator.validateArrayDimension(length, declaredParams).get
			})


	private def expression = (
			(numericLit ^^ { case len => new IntExpression(len.toInt) }) |
			(ident ^^ { case len => new VariableExpression(len)}))

	private def param = rep(paramAttributes) ~ idltype ~ ident ^?
			({ case attributes ~ paramType ~ name if
						SemanticValidator.validateParam(attributes, paramType, name, declaredParams).isEmpty => {
					val param = new Parameter(paramType, name, attributes)
					declaredParams(name) = param
					param
				}
			},
			{
				case attributes ~ paramType ~ name =>
					SemanticValidator.validateParam(attributes, paramType, name, declaredParams).get
			})

	private def paramAttributes = range
	private def range =
			"[" ~> "Range" ~> "(" ~> expression ~ ("," ~> expression) <~ ")" <~ "]" ^^
			{ case minExpr ~ maxExpr => new RangeAttribute(minExpr, maxExpr) }
}

/* vim: set noexpandtab: */
