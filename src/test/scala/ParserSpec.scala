import com.omegaup.libinteractive.idl._

import org.scalatest._

class ParserSpec extends FlatSpec with Matchers {
	"Parser" should "parse interface declarations" in {
		val idl = Parser("""
			interface Main {
			};
			interface child {
			};""")

		idl.interfaces.length should be (1)
		idl.main.name should be ("Main")
		idl.interfaces(0).name should be ("child")
	}

	"Parser" should "support comments" in {
		val idl = Parser("""
			// This is a comment. interface
			interface Main {
			};
			/*
			interface child {
			};*/""")

		idl.interfaces.length should be (0)
	}

	it should "parse function declarations" in {
		val idl = Parser("""
			interface Main {
				void Init(int a, int b);
			};""")

		val functions = idl.main.functions
		functions.length should be (1)
		functions(0).name should be ("Init")
		functions(0).returnType should be (PrimitiveType("void"))

		val params = functions(0).params
		params.length should be (2)
		params(0).paramType should be (PrimitiveType("int"))
		params(0).name should be ("a")
		params(0).attributes.length should be (0)
	}

	it should "support array types" in {
		val idl = Parser("""
			interface Main {
				int determinant(int[3][3] mat);
			};""")

		val param = idl.main.functions(0).params(0)
		param.paramType shouldBe a [ArrayType]
		val arrayType = param.paramType.asInstanceOf[ArrayType]
		arrayType.lengths.length should be (2)
		arrayType.lengths(0) should be (IntExpression(3))
	}

	it should "parse constraints" in {
		val idl = Parser("""
			interface Main {
				void Init([Range(0, 1000)] int a);
			};""")

		val attributes = idl.main.functions(0).params(0).attributes
		attributes.length should be (1)
		attributes(0) shouldBe a [RangeAttribute]
		val range = attributes(0).asInstanceOf[RangeAttribute]
		range.min shouldBe a [IntExpression]
		range.min.asInstanceOf[IntExpression].value should be("0")
		range.max shouldBe a [IntExpression]
		range.max.asInstanceOf[IntExpression].value should be("1000")
	}

	it should "fail" in {
		a [ParseException] should be thrownBy Parser("""
			interface Main;""")

		a [ParseException] should be thrownBy Parser("""
			interface Main {
				int rank(int[n][10] mat);
			};""")

		a [ParseException] should be thrownBy Parser("""
			interface Main {
				int rank(float n, int[n][n] mat);
			};""")

		a [ParseException] should be thrownBy Parser("""
			interface Main {
				int rank(float n, int[n][10] mat);
			};""")
	}

	it should "support multiple interfaces" in {
		val idl = Parser("""
			interface Main {
				void send([Range(0, 65535)] int n);
				void output([Range(0, 255)] int n);
			};

			interface encoder {
				void encode([Range(0, 64)] int N, int[N] M);
			};

			interface decoder {
				void decode([Range(0, 64)] int N, [Range(0, 320)] int L, int[L] X);
			};""")
		idl.interfaces.length should be (2)
	}
}

/* vim: set noexpandtab: */
