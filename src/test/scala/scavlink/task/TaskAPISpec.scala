package scavlink.task

import akka.actor.ActorRef
import org.scalatest.{Matchers, WordSpec}
import scavlink.coord.Geo
import scavlink.link.mission.{Mission, MissionTestData}
import scavlink.message.Mode
import scavlink.message.common.MissionItem

import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._

class TaskAPISpec extends WordSpec with Matchers with MissionTestData {

  val testAPI = TaskAPI[TestAPI]
  val noCtorAPI = TaskAPI[NoCtorAPI]
  val singletonAPI = TaskAPI[SingletonAPI.type]

  "TaskAPI construction" should {
    "construct from class name" in {
      TaskAPI("scavlink.task.TestAPI") shouldBe testAPI
      TaskAPI("scavlink.task.NoCtorAPI") shouldBe noCtorAPI
      TaskAPI("scavlink.task.SingletonAPI") shouldBe singletonAPI
    }

    "fail on invalid class name" in {
      a[ScalaReflectionException] should be thrownBy TaskAPI("xyz")
    }

    "extract simple class name" in {
      testAPI.name shouldBe "TestAPI"
      noCtorAPI.name shouldBe "NoCtorAPI"
      singletonAPI.name shouldBe "SingletonAPI"
    }

    "extract singleton when possible" in {
      assert(!testAPI.isSingleton)
      assert(noCtorAPI.isSingleton)
      assert(singletonAPI.isSingleton)
    }

    "extract constructor with parameters and types" in {
      val method = testAPI.ctor
      method.name shouldBe "<init>"
      method.defaults.keySet shouldBe Set("sender")

      val paramCompare =
        method.params.zip(ListMap(
          "count" -> typeOf[Int],
          "sender" -> typeOf[ActorRef]
        ))

      paramCompare foreach { case (x, y) =>
        x._1 shouldBe y._1
        assert(x._2 =:= y._2)
      }
    }

    "extract method with parameters and types" in {
      val method = testAPI.methods("addValue")
      method.name shouldBe "addValue"
      method.description shouldBe Some("adds the value to the count")
      method.defaults.keySet shouldBe Set("value")

      val paramCompare =
        method.params.zip(ListMap(
          "name" -> typeOf[String],
          "value" -> typeOf[Int]
        ))

      paramCompare foreach { case (x, y) =>
        x._1 shouldBe y._1
        assert(x._2 =:= y._2)
      }
    }

    "expose valid methods" in {
      assert(testAPI.methods.contains("chooseMode"))
      assert(testAPI.methods.contains("addValue"))
      assert(testAPI.methods.contains("doubleParamList"))
    }

    "allow methods with collection parameters" in {
      assert(testAPI.methods.contains("validCollectionTypes"))
    }

    "not expose method with no ActorRef" in {
      assert(!noCtorAPI.methods.contains("noActor"))
    }

    "not expose method with more than one actor" in {
      assert(!noCtorAPI.methods.contains("duplicateActor"))
    }

    "not expose method with invalid types" in {
      assert(!testAPI.methods.contains("invalidType"))
      assert(!testAPI.methods.contains("invalidCollectionType"))
    }
  }

  "TaskAPI.isAllowedParamType" should {
    "recognize Map[String, _]" in {
      TaskAPI.isAllowedParamType(typeOf[Map[String, Int]]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[Map[String, Double]]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[Map[String, MissionItem]]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[Map[Int, Double]]) shouldBe false
      TaskAPI.isAllowedParamType(typeOf[Map[String, TaskAPI]]) shouldBe false
    }

    "recognize List, Set, Vector" in {
      TaskAPI.isAllowedParamType(typeOf[List[String]]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[List[MissionItem]]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[List[(Int, Float)]]) shouldBe false

      TaskAPI.isAllowedParamType(typeOf[Set[Int]]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[Set[Mode]]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[Set[TaskAPI]]) shouldBe false

      TaskAPI.isAllowedParamType(typeOf[Vector[Int]]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[Vector[Geo]]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[Mission]) shouldBe true
      TaskAPI.isAllowedParamType(typeOf[Vector[TaskAPI]]) shouldBe false
    }
  }

  "TaskAPI invocation" should {
    "invoke an ordinary method correctly" in {
      val result = singletonAPI.invoke("echoName", Map("name" -> "what"))
      result shouldBe "what"
    }

    "invoke a method with non-primitive types" in {
      val result = testAPI.invoke("chooseMode",
        Map("count" -> 0, "mode1" -> Mode.Auto, "mode2" -> Mode.Guided, "first" -> true))
      result shouldBe Mode.Auto
    }

    "invoke a method where some parameters are constructor arguments" in {
      val result = testAPI.invoke("addValue", Map("count" -> 4, "name" -> "what", "value" -> 4))
      result shouldBe 8
    }

    "invoke a method with a default parameter value" in {
      val result = testAPI.invoke("doubleParamList", Map(
        "count" -> 4,
        "name" -> "what",
        "num" -> 3,
        "alt" -> 7
      ))
      result shouldBe 11
    }

    "invoke a method with a default value in a second parameter list" in {
      val result = testAPI.invoke("addValue", Map("count" -> 4, "name" -> "what"))
      result shouldBe 14
    }

    "invoke a method with collection parameters" in {
      val result = testAPI.invoke("validCollectionTypes", Map(
        "count" -> 0,
        "point" -> waypoints.head,
        "set" -> Set(waypoints: _*),
        "mission" -> missionFromFile,
        "polygon" -> List(waypoints(0).latlon, waypoints(1).latlon),
        "map" -> Map("a" -> 1, "b" -> 2)
      ))
      val item = missionFromFile.head
      result shouldBe Geo(item.x, item.y, item.z)
    }

    "throw MethodNotFound if method name is invalid" in {
      a[MethodNotFoundException] should be thrownBy testAPI.invoke("whatever", Map.empty)
    }

    "throw ParameterMissing if a required parameter is missing" in {
      a[ParameterMissingException] should be thrownBy testAPI.invoke("chooseMode", Map.empty)
    }

    "throw MethodNotFound if someone else's method is invoked" in {
      a[MethodNotFoundException] should be thrownBy testAPI.invokeMethod(noCtorAPI.methods.values.head, Map("count" -> 0))
    }
  }
}
