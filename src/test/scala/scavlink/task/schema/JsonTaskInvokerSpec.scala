package scavlink.task.schema

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.json4s.Extraction
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import scavlink.coord.{Geo, LatLon}
import scavlink.link.Vehicle
import scavlink.link.mission._
import scavlink.link.nav.NavTellAPI.Nav
import scavlink.link.operation.VehicleOpSpecSupport
import scavlink.message.Mode
import scavlink.message.common.MissionItem
import scavlink.task.{MethodNotFoundException, ParameterMissingException, TaskAPI, TestAPI}

import scala.collection.immutable.Queue
import scala.reflect.runtime.universe._

class JsonTaskInvokerSpec(_system: ActorSystem) extends VehicleOpSpecSupport(_system)
with ImplicitSender with MissionTestData {
  def this() = this(ActorSystem("JsonTaskInvoker"))
  override def afterAll() = TestKit.shutdownActorSystem(system)

  val testAPI = TaskAPI[TestAPI]
  val navAPI = TaskAPI[Nav]
  val invokerAPI = TaskAPI[InvokerTestAPI]
  val invoker = new JsonTaskInvoker(List(testAPI, navAPI, invokerAPI), Map(id -> vehicle), self)

  import invoker.formats

  def invoke[T: TypeTag](args: JObject): Any = {
    val typ = typeOf[T]
    val name = typ.typeSymbol.name.decodedName.toString
    val json: JObject = s"InvokerTestAPI.call$name" -> args
    invoker.invokeJson(json)
  }

  def invokeNumeric[T: TypeTag](x: Int, r: T): Unit = {
    val json: JObject = "x" -> x
    invoke[T](json) shouldBe r
  }


  "the task invoker" should {
    "invoke an ordinary method from a json string" in {
      val json = "{ \"TestAPI.addValue\": { \"count\": 4, \"name\": \"what\", \"value\": 8 } }"
      val result = invoker.invokeJsonString(json)
      result shouldBe 12
    }

    "fail to parse an invalid json string" in {
      val json = "yyy"
      an [InvalidJsonException] should be thrownBy invoker.invokeJsonString(json)
    }

    "invoke an ordinary method from json" in {
      val json: JObject =
        "TestAPI.addValue" ->
        ("count" -> 4) ~
        ("name" -> "what") ~
        ("value" -> 8)

      val result = invoker.invokeJson(json)
      result shouldBe 12
    }

    "fail if json has more than one field" in {
      val json: JObject =
        ("InvokerTestAPI.callInt" -> ("x" -> 1)) ~
        ("TestAPI.addValue" ->
         ("count" -> 4) ~
         ("name" -> "what") ~
         ("value" -> 8))

      an [InvalidJsonException] should be thrownBy invoker.invokeJson(json)
    }

    "fail if not all parameters are provided" in {
      val json: JObject =
        "TestAPI.addValue" ->
        ("count" -> 4) ~
        ("value" -> 8)

      an [ParameterMissingException] should be thrownBy invoker.invokeJson(json)
    }

    "invoke with numeric types" in {
      invokeNumeric(1, 2.toByte)
      invokeNumeric(1, 2.toShort)
      invokeNumeric(1, 2)
      invokeNumeric(1, 2L)
      invokeNumeric(1, 2f)
      invokeNumeric(1, 2d)
    }

    "invoke with string type" in {
      val jstr: JObject = "x" -> "hello"
      invoke[String](jstr) shouldBe "hello_"
    }

    "invoke with boolean type" in {
      val jbool: JObject = "x" -> true
      invoke[Boolean](jbool) shouldBe false
    }

    "invoke with collection types" in {
      val jlist: JObject = "x" -> List(1, 2, 3)
      invoke[List[Int]](jlist) shouldBe List(2, 3, 4)

      val jvector: JObject = "x" -> Vector(1, 2, 3)
      invoke[Vector[Int]](jvector) shouldBe Vector(2, 3, 4)

      val jset: JObject = "x" -> Set(1, 2, 3)
      invoke[Set[Int]](jset) shouldBe Set(2, 3, 4)

      val jmap: JObject = "x" -> Map("a" -> 1, "b" -> 2, "c" -> 3)
      invoke[Map[String, Int]](jmap) shouldBe Map("a" -> 2, "b" -> 3, "c" -> 4)
    }

    "fail to invoke with unsupported collection types" in {
      val jqueue: JObject = "x" -> Queue(1, 2, 3)
      a[MethodNotFoundException] should be thrownBy invoke[Queue[Int]](jqueue)
    }

    "invoke with Mode" in {
      val json: JObject = "x" -> Extraction.decompose(Mode.Loiter)
      invoke[Mode](json) shouldBe Mode.Loiter
    }

    "invoke with LatLon" in {
      val json: JObject = "x" -> Extraction.decompose(LatLon(45, 75))
      invoke[LatLon](json) shouldBe LatLon(75, 45)
    }

    "invoke with Geo" in {
      val json: JObject = "x" -> Extraction.decompose(Geo(45, 75, 10))
      invoke[Geo](json) shouldBe Geo(75, 45, 10)
    }

    "invoke with MissionItem" in {
      val json: JObject = "x" -> Extraction.decompose(missionFromFile(0))
      invoke[MissionItem](json) shouldBe missionFromFile(0)
    }

    "invoke with Mission" in {
      val json: JObject = s"InvokerTestAPI.callMission" -> ("x" -> Extraction.decompose(missionFromFile))
      val result = invoker.invokeJson(json).asInstanceOf[Mission]
      result.zip(missionFromFile).foreach { case (x, y) => x shouldBe y }
    }

    "invoke with Vehicle" in {
      val json: JObject = "x" -> Extraction.decompose(vehicle)
      invoke[Vehicle](json) shouldBe vehicle
    }

    "invoke with VehicleId" in {
      val json: JObject = "x" -> Extraction.decompose(id)
      invoke[Vehicle](json) shouldBe vehicle
    }

    "invoke with VehicleId string" in {
      val json: JObject = "x" -> "mock#1"
      invoke[Vehicle](json) shouldBe vehicle
    }

    "invoke with invalid VehicleId string" in {
      val json: JObject = "x" -> "mock#2"
      a[ParameterMissingException] should be thrownBy invoke[Vehicle](json)
    }
  }

  "NavAPI" should {
    "invoke armMotors" in {
      val json = "{ \"Nav.armMotors\": {" +
                 " \"vehicle\": \"" + id + "\"," +
                 " \"shouldArm\": true" +
                 " } }"
      invoker.invokeJsonString(json)
    }
  }
}

class InvokerTestAPI(sender: ActorRef = Actor.noSender) {
  // test that the TestKit ImplicitSender was passed in
  assert(sender != Actor.noSender)

  def callString(x: String): String = x + "_"
  def callBoolean(x: Boolean): Boolean = !x

  def callByte(x: Byte): Byte = (x + 1).toByte
  def callShort(x: Short): Short = (x + 1).toShort
  def callInt(x: Int): Int = x + 1
  def callLong(x: Long): Long = x + 1
  def callFloat(x: Float): Float = x + 1
  def callDouble(x: Double): Double = x + 1

  def callVector(x: Vector[Int]): Vector[Int] = x.map(_ + 1)
  def callList(x: List[Int]): List[Int] = x.map(_ + 1)
  def callSet(x: Set[Int]): Set[Int] = x.map(_ + 1)
  def callMap(x: Map[String, Int]): Map[String, Int] = x.mapValues(_ + 1)

  def callQueue(x: Queue[Int]): Queue[Int] = x.map(_ + 1) // failure case

  def callVehicle(x: Vehicle): Vehicle = x
  def callMode(x: Mode): Mode = x
  def callLatLon(x: LatLon): LatLon = LatLon(x.lon, x.lat)
  def callGeo(x: Geo): Geo = Geo(x.lon, x.lat, x.alt)
  def callMissionItem(x: MissionItem): MissionItem = x
  def callMission(x: Mission): Mission = x
}
