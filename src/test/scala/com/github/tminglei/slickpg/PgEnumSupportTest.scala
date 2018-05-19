package com.github.tminglei.slickpg

import org.junit._
import org.junit.Assert._
import scala.slick.driver.PostgresDriver
import scala.util.Try

class PgEnumSupportTest {
  object WeekDays extends Enumeration {
    type WeekDay = Value
    val Mon, Tue, Wed, Thu, Fri, Sat, Sun = Value
  }
  object Rainbows extends Enumeration {
    type Rainbow = Value
    val red, orange, yellow, green, blue, purple = Value
  }

  import WeekDays._
  import Rainbows._

  trait MyPostgresDriver1 extends PostgresDriver with PgEnumSupport {
    override lazy val Implicit = new Implicits with MyEnumImplicits {}
    override val simple = new SimpleQL with MyEnumImplicits {}

    trait MyEnumImplicits {
      implicit val weekDayTypeMapper = createEnumJdbcType("WeekDay", WeekDays)
      implicit val weekDayListTypeMapper = createEnumListJdbcType("weekDay", WeekDays)
      implicit val rainbowTypeMapper = createEnumJdbcType("Rainbow", Rainbows, true)
      implicit val rainbowListTypeMapper = createEnumListJdbcType("Rainbow", Rainbows, true)
      
      implicit val weekDayColumnExtensionMethodsBuilder = createEnumColumnExtensionMethodsBuilder(WeekDays)
      implicit val weekDayOptionColumnExtensionMethodsBuilder = createEnumOptionColumnExtensionMethodsBuilder(WeekDays)
      implicit val rainbowColumnExtensionMethodsBuilder = createEnumColumnExtensionMethodsBuilder(Rainbows)
      implicit val rainbowOptionColumnExtensionMethodsBuilder = createEnumOptionColumnExtensionMethodsBuilder(Rainbows)
    }
  }
  object MyPostgresDriver1 extends MyPostgresDriver1

  ////////////////////////////////////////////////////////////////////
  import MyPostgresDriver1.simple._

  val db = Database.forURL(url = dbUrl, driver = "org.postgresql.Driver")

  case class TestEnumBean(id: Long, weekday: WeekDay, rainbow: Option[Rainbow], weekdays: List[WeekDay], rainbows: List[Rainbow])
  
  class TestEnumTable(tag: Tag) extends Table[TestEnumBean](tag, "test_enum_table") {
    def id = column[Long]("id")
    def weekday = column[WeekDay]("weekday", O.Default(Mon))
    def rainbow = column[Option[Rainbow]]("rainbow")
    def weekdays = column[List[WeekDay]]("weekdays")
    def rainbows = column[List[Rainbow]]("rainbows")
    
    def * = (id, weekday, rainbow, weekdays, rainbows) <> (TestEnumBean.tupled, TestEnumBean.unapply)
  }
  val TestEnums = TableQuery(new TestEnumTable(_))
  
  //------------------------------------------------------------------
  
  val testRec1 = TestEnumBean(101L, Mon, Some(red), Nil, List(red, yellow))
  val testRec2 = TestEnumBean(102L, Wed, Some(blue), List(Sat, Sun), List(green))
  val testRec3 = TestEnumBean(103L, Fri, None, List(Thu), Nil)
  
  @Test
  def testEnumFunctions(): Unit = {
    db withSession { implicit session: Session =>
      TestEnums insertAll (testRec1, testRec2, testRec3)
      
      val q0 = TestEnums.sortBy(_.id).map(t => t)
      assertEquals(List(testRec1, testRec2, testRec3), q0.list)
      
      val q1 = TestEnums.filter(_.id === 101L.bind).map(t => t.weekday.first)
      println(s"[enum] 'first' sql = ${q1.selectStatement}")
      assertEquals(Mon, q1.first)
      
      val q2 = TestEnums.filter(_.id === 101L.bind).map(t => t.rainbow.last)
      println(s"[enum] 'last' sql = ${q2.selectStatement}")
      assertEquals(Some(purple), q2.first)

      val q3 = TestEnums.filter(_.id === 101L.bind).map(t => t.weekday.all)
      println(s"[enum] 'all' sql = ${q3.selectStatement}")
      assertEquals(WeekDays.values.toList, q3.first)

      val q4 = TestEnums.filter(_.id === 102L.bind).map(t => t.weekday range null.asInstanceOf[WeekDay])
      println(s"[enum] 'range' sql = ${q4.selectStatement}")
      assertEquals(List(Wed, Thu, Fri, Sat, Sun), q4.first)

      val q41 = TestEnums.filter(_.id === 102L.bind).map(t => null.asInstanceOf[WeekDay].bind range t.weekday)
      println(s"[enum] 'range' sql.1 = ${q41.selectStatement}")
      assertEquals(List(Mon, Tue, Wed), q41.first)
    }
  }
  
  //------------------------------------------------------------------

  @Before
  def createTables(): Unit = {
    db withSession { implicit session: Session =>
      // clear first
      Try { TestEnums.ddl drop }
      Try { PgEnumSupportUtils.buildDropSql("weekday").execute }
      Try { PgEnumSupportUtils.buildDropSql("Rainbow", true).execute }

      // then create
      Try { PgEnumSupportUtils.buildCreateSql("WeekDay", WeekDays).execute }.get
      Try { PgEnumSupportUtils.buildCreateSql("Rainbow", Rainbows, true).execute }.get

      Try { TestEnums.ddl.createStatements.foreach(s => println(s"[enum] $s")) }
      Try { TestEnums.ddl create }
    }
  }
}
