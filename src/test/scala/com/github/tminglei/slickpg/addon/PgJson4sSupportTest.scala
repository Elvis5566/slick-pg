package com.github.tminglei.slickpg

import org.junit._
import org.junit.Assert._
import org.json4s._
import scala.slick.jdbc.{StaticQuery => Q, GetResult}
import scala.util.Try

class PgJson4sSupportTest {
  import scala.slick.driver.PostgresDriver

  trait MyPostgresDriver extends PostgresDriver
                            with PgJson4sSupport
                            with array.PgArrayJdbcTypes {
    /// for json support
    override val pgjson = "jsonb"
    type DOCType = text.Document
    override val jsonMethods = org.json4s.native.JsonMethods

    override lazy val Implicit = new Implicits with JsonImplicits {}
    override val simple = new Simple {}

    trait Simple extends Implicits with SimpleQL with JsonImplicits {
      implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)
    }

    val plainImplicits = new Implicits with Json4sJsonPlainImplicits {}
  }
  object MyPostgresDriver extends MyPostgresDriver

  ///
  import MyPostgresDriver.simple._
  import MyPostgresDriver.jsonMethods._

  val db = Database.forURL(url = dbUrl, driver = "org.postgresql.Driver")

  case class JsonBean(id: Long, json: JValue)

  class JsonTestTable(tag: Tag) extends Table[JsonBean](tag, "JsonTest1") {
    def id = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def json = column[JValue]("json")

    def * = (id, json) <> (JsonBean.tupled, JsonBean.unapply)
  }
  val JsonTests = TableQuery[JsonTestTable]

  //------------------------------------------------------------------------------

  val testRec1 = JsonBean(33L, parse(""" { "a":101, "b":"aaa", "c":[3,4,5,9] } """))
  val testRec2 = JsonBean(35L, parse(""" [ {"a":"v1","b":2}, {"a":"v5","b":3} ] """))
  val testRec3 = JsonBean(37L, parse(""" ["a", "b"] """))

  @Test
  def testJsonFunctions(): Unit = {
    db withSession { implicit session: Session =>
      Try { JsonTests.ddl drop }
      Try { JsonTests.ddl create }

      JsonTests forceInsertAll (testRec1, testRec2, testRec3)

      val json1 = parse(""" {"a":"v1","b":2} """)
      val json2 = parse(""" {"a":"v5","b":3} """)

      val q0 = JsonTests.filter(_.id === testRec2.id.bind).map(_.json)
      println(s"[json4s] sql0 = ${q0.selectStatement}")
      assertEquals(JArray(List(json1,json2)), q0.first)

      /* pretty(render(JInt(101))) will get "101", but parse("101") will fail, since json string must start with '{' or '[' */
//      val q1 = JsonTests.filter(_.id === testRec1.id.bind).map(_.json.+>("a"))
//      println(s"[json] '+>' sql = ${q1.selectStatement}")
//      assertEquals(JInt(101), q1.first)

      val q11 = JsonTests.filter(_.json.+>>("a") === "101").map(_.json.+>>("c"))
      println(s"[json4s] '+>>' sql = ${q11.selectStatement}")
      assertEquals("[3,4,5,9]", q11.first.replace(" ", ""))

      val q12 = JsonTests.filter(_.json.+>>("a") === "101".bind).map(_.json.+>("c"))
      println(s"[json4s] '+>' sql = ${q12.selectStatement}")
      assertEquals(JArray(List(JInt(3), JInt(4), JInt(5), JInt(9))), q12.first)

      // json array's index starts with 0
      val q2 = JsonTests.filter(_.id === testRec2.id).map(_.json.~>(1))
      println(s"[json4s] '~>' sql = ${q2.selectStatement}")
      assertEquals(json2, q2.first)

      val q21 = JsonTests.filter(_.id === testRec2.id).map(_.json.~>>(1))
      println(s"[json4s] '~>>' sql = ${q21.selectStatement}")
      assertEquals("""{"a":"v5","b":3}""", q21.first.replace(" ", ""))

      /* disable it, because operator does not exist: json = json */
//      val q3 = JsonTests.filter(_.json.#>(List("c")) === parse("[3,4,5,9]")).map(r => r)
//      println(s"[json] '#>' sql = ${q3.selectStatement}")
//      assertEquals(testRec1, q3.first)

      val q31 = JsonTests.filter(_.id === testRec1.id).map(_.json.#>(List("c")))
      println(s"[json4s] '#>' sql = ${q31.selectStatement}")
      assertEquals(parse("[3,4,5,9]"), q31.first)

      val q4 = JsonTests.filter(_.json.#>>(List("a")) === "101").map(r => r)
      println(s"[json4s] '#>>' sql = ${q4.selectStatement}")
      assertEquals(testRec1, q4.first)

      val q5 = JsonTests.filter(_.id === testRec2.id).map(_.json.arrayLength)
      println(s"[json4s] 'arrayLength' sql = ${q5.selectStatement}")
      assertEquals(2, q5.first)

      val q6 = JsonTests.filter(_.id === testRec2.id).map(_.json.arrayElements)
      println(s"[json4s] 'arrayElements' sql = ${q6.selectStatement}")
      assertEquals(List(json1, json2), q6.list)

      val q61 = JsonTests.filter(_.id === testRec2.id).map(_.json.arrayElements)
      println(s"[json4s] 'arrayElements' sql = ${q61.selectStatement}")
      assertEquals(json1, q61.first)

      val q62 = JsonTests.filter(_.id === testRec2.id).map(_.json.arrayElementsText)
      println(s"[json4s] 'arrayElementsText' sql = ${q62.selectStatement}")
      assertEquals(compact(render(json1)), q62.first.replace(" ", ""))

      val q7 = JsonTests.filter(_.id === testRec1.id).map(_.json.objectKeys)
      println(s"[json4s] 'objectKeys' sql = ${q7.selectStatement}")
      assertEquals(List("a","b","c"), q7.list)

      val q71 = JsonTests.filter(_.id === testRec1.id).map(_.json.objectKeys)
      println(s"[json4s] 'objectKeys' sql = ${q71.selectStatement}")
      assertEquals("a", q71.first)

      val q8 = JsonTests.filter(_.json @> parse(""" {"b":"aaa"} """)).map(_.id)
      println(s"[json4s] '@>' sql = ${q8.selectStatement}")
      assertEquals(33L, q8.first)

      val q81 = JsonTests.filter(_.json @> parse(""" [{"a":"v5"}] """)).map(_.id)
      println(s"[json4s] '@>' sql = ${q81.selectStatement}")
      assertEquals(35L, q81.first)

      val q9 = JsonTests.filter(parse(""" {"b":"aaa"} """) <@: _.json).map(_.id)
      println(s"[json4s] '<@' sql = ${q9.selectStatement}")
      assertEquals(33L, q9.first)

      val q10 = JsonTests.filter(_.id === testRec1.id).map(_.json.+>("a").jsonType)
      println(s"[json4s] 'typeof' sql = ${q10.selectStatement}")
      assertEquals("number", q10.first.toLowerCase)

      val q15 = JsonTests.filter(_.json ?? "b".bind).map(r => r)
      println(s"[json4s] '??' sql = ${q15.selectStatement}")
      assertEquals(List(testRec1, testRec3), q15.list)

      val q15a = JsonTests.filter(_.json ?| List("a", "c").bind).map(r => r)
      println(s"[json4s] '?|' sql = ${q15a.selectStatement}")
      assertEquals(List(testRec1, testRec3), q15a.list)

      val q15b = JsonTests.filter(_.json ?& List("a", "c").bind).map(r => r)
      println(s"[json4s] '?&' sql = ${q15b.selectStatement}")
      assertEquals(List(testRec1), q15b.list)
    }
  }

  //------------------------------------------------------------------------------

  @Test
  def testPlainJsonFunctions(): Unit = {
    import MyPostgresDriver.plainImplicits._

    implicit val getJsonBeanResult = GetResult(r => JsonBean(r.nextLong(), r.nextJson()))

    db withSession { implicit session: Session =>
      Try { JsonTests.ddl drop }
      Try { JsonTests.ddl create }

      val jsonBean = JsonBean(37L, parse(""" { "a":101, "b":"aaa", "c":[3,4,5,9] } """))

      (Q.u + "insert into \"JsonTest1\" values(" +? jsonBean.id + ", " +? jsonBean.json + ")").execute

      val found = (Q[JsonBean] + "select * from \"JsonTest1\" where id = " +? jsonBean.id).first

      assertEquals(jsonBean, found)
    }
  }
}
