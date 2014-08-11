package com.omegaup.libinteractive.idl

import scala.collection.Iterable
import scala.collection.mutable.HashMap
import scala.util.parsing.combinator.syntactical._

case class ParseException(msg: String, longString: String, line: Int, column: Int)
		extends RuntimeException {
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
case class ArrayType(primitive: PrimitiveType, lengths: List[Expression]) extends Type {
	override def children() = List(primitive) ++ lengths
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
	def validateFunction(returnType: PrimitiveType, name: String, params: List[Parameter]): Option[String] = {
		val declaredParams = HashMap.empty[String, Type]
		for (param <- params) {
			if (declaredParams.contains(param.name)) {
				return Some(s"Parameter `${param.name}' is declared more than " +
					s"once in function `$name'")
			}
			param.paramType match {
				case array: ArrayType => {
					for (expr <- array.lengths) {
						expr match {
							case variable: VariableExpression => {
								if (!declaredParams.contains(variable.value)) {
									return Some(s"Array index `${variable.value}' must have " +
										s"been passed as a previous parameter in function `$name'")
								}
								if (declaredParams(variable.value) != PrimitiveType("int")) {
									return Some(s"Array index `${variable.value}' must be " +
										"declared as int")
								}
							}
							case _ => {}
						}
					}
				}
				case _ => {}
			}
			declaredParams(param.name) = param.paramType
		}
		None
	}

	def validateArrayType(primitive: PrimitiveType, lengths: List[Expression]): Option[String] = {
		for ((length, idx) <- lengths.view.zipWithIndex) {
			length match {
				case _: VariableExpression => {
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

object Parser extends StandardTokenParsers {
	lexical.delimiters ++= List("(", ")", "[", "]", "{", "}", ",", ";")
	lexical.reserved += (
			"bool", "int", "short", "float", "char", "string", "long", "void",
			"interface", "Range")

	def apply(input: String): IDL = {
		interfaceList(new lexical.Scanner(input)) match {
			case Success(interfaces, _) => {
				interfaces
			}
			case NoSuccess(msg, err) => {
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
			primitive ~ ident ~ ("(" ~> repsep(param, ",") <~ ")") <~ ";" ^?
			({
				case returnType ~ name ~ params if
						SemanticValidator.validateFunction(returnType, name, params).isEmpty =>
					new Function(returnType, name, params)
			},
			{
				case returnType ~ name ~ params =>
						SemanticValidator.validateFunction(returnType, name, params).get
			})

	private def idltype = array | primitive
	private def primitive =
			("bool" | "int" | "short" | "float" | "char" | "string" | "long" |
			"void") ^^
			{ case name => new PrimitiveType(name) }
	private def array = primitive ~ rep1("[" ~> expression <~ "]") ^?
			({
				case baseType ~ lengths if
						SemanticValidator.validateArrayType(baseType, lengths).isEmpty =>
					new ArrayType(baseType, lengths)
			},
			{
				case baseType ~ lengths =>
						SemanticValidator.validateArrayType(baseType, lengths).get
			})

	private def expression = (
			(numericLit ^^ { case len => new IntExpression(len.toInt) }) |
			(ident ^^ { case len => new VariableExpression(len)}))

	private def param = rep(paramAttributes) ~ idltype ~ ident ^^
			{ case attributes ~ paramType ~ name =>
					new Parameter(paramType, name, attributes) }

	private def paramAttributes = range
	private def range =
			"[" ~> "Range" ~> "(" ~> expression ~ ("," ~> expression) <~ ")" <~ "]" ^^
			{ case minExpr ~ maxExpr => new RangeAttribute(minExpr, maxExpr) }
}

/* vim: set noexpandtab: */
