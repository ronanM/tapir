package tapir.generic

import java.math.{BigDecimal => JBigDecimal}

import org.scalatest.{FlatSpec, Matchers}
import tapir.Schema._
import tapir.{Schema, SchemaFor}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

case class StringValueClass(value: String) extends AnyVal
case class IntegerValueClass(value: Int) extends AnyVal

class SchemaForTest extends FlatSpec with Matchers {

  "SchemaFor" should "find schema for simple types" in {
    implicitly[SchemaFor[String]].schema shouldBe SString
    implicitly[SchemaFor[String]].isOptional shouldBe false

    implicitly[SchemaFor[Short]].schema shouldBe SInteger
    implicitly[SchemaFor[Int]].schema shouldBe SInteger
    implicitly[SchemaFor[Long]].schema shouldBe SInteger
    implicitly[SchemaFor[Float]].schema shouldBe SNumber
    implicitly[SchemaFor[Double]].schema shouldBe SNumber
    implicitly[SchemaFor[Boolean]].schema shouldBe SBoolean
    implicitly[SchemaFor[BigDecimal]].schema shouldBe SString
    implicitly[SchemaFor[JBigDecimal]].schema shouldBe SString
  }

  it should "find schema for value classes" in {
    implicitly[SchemaFor[StringValueClass]].schema shouldBe SString
    implicitly[SchemaFor[IntegerValueClass]].schema shouldBe SInteger
  }

  it should "find schema for collections of value classes" in {
    implicitly[SchemaFor[Array[StringValueClass]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[Array[IntegerValueClass]]].schema shouldBe SArray(SInteger)
  }

  it should "find schema for optional types" in {
    implicitly[SchemaFor[Option[String]]].schema shouldBe SString
    implicitly[SchemaFor[Option[String]]].isOptional shouldBe true
  }

  it should "find schema for collections" in {
    implicitly[SchemaFor[Array[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[Array[String]]].isOptional shouldBe false

    implicitly[SchemaFor[List[String]]].schema shouldBe SArray(SString)
    implicitly[SchemaFor[List[String]]].isOptional shouldBe false

    implicitly[SchemaFor[Set[String]]].schema shouldBe SArray(SString)
  }

  val expectedASchema =
    SProduct(SObjectInfo("tapir.generic.A"), List(("f1", SString), ("f2", SInteger), ("f3", SString)), List("f1", "f2"))

  it should "find schema for collections of case classes" in {
    implicitly[SchemaFor[List[A]]].schema shouldBe SArray(expectedASchema)
  }

  it should "find schema for a simple case class" in {
    implicitly[SchemaFor[A]].schema shouldBe expectedASchema
  }

  val expectedDSchema: SProduct = SProduct(SObjectInfo("tapir.generic.D"), List(("someFieldName", SString)), List("someFieldName"))

  it should "find schema for a simple case class and use identity naming transformation" in {
    implicitly[SchemaFor[D]].schema shouldBe expectedDSchema
  }

  it should "find schema for a simple case class and use snake case naming transformation" in {
    val expectedSnakeCaseNaming = expectedDSchema.copy(fields = List(("some_field_name", SString)), required = List("some_field_name"))
    implicit val customConf: Configuration = Configuration.default.withSnakeCaseMemberNames
    implicitly[SchemaFor[D]].schema shouldBe expectedSnakeCaseNaming
  }

  it should "find schema for a simple case class and use kebab case naming transformation" in {
    val expectedKebabCaseNaming = expectedDSchema.copy(fields = List(("some-field-name", SString)), required = List("some-field-name"))
    implicit val customConf: Configuration = Configuration.default.withKebabCaseMemberNames
    implicitly[SchemaFor[D]].schema shouldBe expectedKebabCaseNaming
  }

  it should "find schema for a nested case class" in {
    implicitly[SchemaFor[B]].schema shouldBe SProduct(
      SObjectInfo("tapir.generic.B"),
      List(("g1", SString), ("g2", expectedASchema)),
      List("g1", "g2")
    )
  }

  it should "find schema for case classes with collections" in {
    implicitly[SchemaFor[C]].schema shouldBe SProduct(
      SObjectInfo("tapir.generic.C"),
      List(("h1", SArray(SString)), ("h2", SInteger)),
      List("h1")
    )
  }

  it should "find schema for recursive data structure" in {
    val schema = implicitly[SchemaFor[F]].schema
    schema shouldBe SProduct(
      SObjectInfo("tapir.generic.F"),
      List(("f1", SArray(SRef(SObjectInfo("tapir.generic.F")))), ("f2", SInteger)),
      List("f1", "f2")
    )
  }

  it should "find schema for recursive data structure when invoked from many threads" in {
    val expected =
      SProduct(
        SObjectInfo("tapir.generic.F"),
        List(("f1", SArray(SRef(SObjectInfo("tapir.generic.F")))), ("f2", SInteger)),
        List("f1", "f2")
      )

    val count = 100
    val futures = (1 until count).map { _ =>
      Future[Schema] {
        implicitly[SchemaFor[F]].schema
      }
    }

    val eventualSchemas = Future.sequence(futures)

    val schemas = Await.result(eventualSchemas, 5 seconds)
    schemas should contain only expected
  }

  it should "use custom schema for custom types" in {
    implicit val scustom: SchemaFor[Custom] = SchemaFor[Custom](Schema.SString)
    val schema = implicitly[SchemaFor[G]].schema
    schema shouldBe SProduct(SObjectInfo("tapir.generic.G"), List(("f1", SInteger), ("f2", SString)), List("f1", "f2"))
  }

  it should "derive schema for parametrised type classes" in {
    val schema = implicitly[SchemaFor[H[A]]].schema
    schema shouldBe SProduct(SObjectInfo("tapir.generic.H", List("A")), List(("data", expectedASchema)), List("data"))
  }

  it should "find schema for map" in {
    val schema = implicitly[SchemaFor[Map[String, Int]]].schema
    schema shouldBe SOpenProduct(SObjectInfo("Map", List("Int")), Schema.SInteger)
  }

  it should "find schema for map of products" in {
    val schema = implicitly[SchemaFor[Map[String, D]]].schema
    schema shouldBe SOpenProduct(
      SObjectInfo("Map", List("D")),
      SProduct(SObjectInfo("tapir.generic.D"), List(("someFieldName", SString)), List("someFieldName"))
    )
  }

  it should "find schema for map of generic products" in {
    val schema = implicitly[SchemaFor[Map[String, H[D]]]].schema
    schema shouldBe SOpenProduct(
      SObjectInfo("Map", List("H", "D")),
      SProduct(
        SObjectInfo("tapir.generic.H", List("D")),
        List(("data", SProduct(SObjectInfo("tapir.generic.D"), List(("someFieldName", SString)), List("someFieldName")))),
        List("data")
      )
    )
  }

  it should "find schema for map of value classes" in {
    val schema = implicitly[SchemaFor[Map[String, IntegerValueClass]]].schema
    schema shouldBe SOpenProduct(SObjectInfo("Map", List("IntegerValueClass")), Schema.SInteger)
  }

  it should "find schema for recursive coproduct type" in {
    val schema = implicitly[SchemaFor[Node]].schema
    schema shouldBe SCoproduct(
      SObjectInfo("tapir.generic.Node", List.empty),
      Set(
        SProduct(
          SObjectInfo("tapir.generic.Edge"),
          List(
            "id" -> SInteger,
            "source" ->
              SCoproduct(
                SObjectInfo("tapir.generic.Node", List.empty),
                Set(
                  SRef(SObjectInfo("tapir.generic.Edge")),
                  SProduct(
                    SObjectInfo("tapir.generic.SimpleNode"),
                    List("id" -> SInteger),
                    List("id")
                  )
                ),
                None
              )
          ),
          List("id", "source")
        ),
        SProduct(
          SObjectInfo("tapir.generic.SimpleNode"),
          List("id" -> SInteger),
          List("id")
        )
      ),
      None
    )
  }

  it should "find the right schema for a case class with simple types" in {
    // given
    case class Test1(
        f1: String,
        f2: Byte,
        f3: Short,
        f4: Int,
        f5: Long,
        f6: Float,
        f7: Double,
        f8: Boolean,
        f9: BigDecimal,
        f10: JBigDecimal
    )
    val schema = implicitly[SchemaFor[Test1]]

    // when
    schema.schema shouldBe SProduct(
      SObjectInfo("tapir.generic.SchemaForTest.<local SchemaForTest>.Test1"),
      List(
        ("f1", SString),
        ("f2", SInteger),
        ("f3", SInteger),
        ("f4", SInteger),
        ("f5", SInteger),
        ("f6", SNumber),
        ("f7", SNumber),
        ("f8", SBoolean),
        ("f9", SString),
        ("f10", SString)
      ),
      List("f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10")
    )
  }
}

case class A(f1: String, f2: Int, f3: Option[String])
case class B(g1: String, g2: A)
case class C(h1: List[String], h2: Option[Int])
case class D(someFieldName: String)
case class F(f1: List[F], f2: Int)

class Custom(c: String)
case class G(f1: Int, f2: Custom)

case class H[T](data: T)

sealed trait Node
case class Edge(id: Long, source: Node) extends Node
case class SimpleNode(id: Long) extends Node
