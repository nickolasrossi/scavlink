package scavlink.task

import akka.actor.{Actor, ActorRef}
import scavlink.coord.{Geo, LatLon}
import scavlink.link.mission._
import scavlink.message.Mode

import scala.collection.immutable.Queue


class TestAPI(count: Int, sender: ActorRef = Actor.noSender) {
  def chooseMode(mode1: Mode, mode2: Mode, first: Boolean): Mode = {
    println(s"chooseMode: $count, $sender, $mode1, $mode2, $first")
    if (first) mode1 else mode2
  }

  @Description("adds the value to the count")
  def addValue(name: String, value: Int = 10): Int = {
    println(s"addValue: $count, $sender, $name, $value")
    value + count
  }

  def doubleParamList(num: Int, alt: Double = 5)(name: String, lat: Double = alt): Double = {
    println(s"doubleParamList: $count, $sender, $num, $alt, $name, $lat")
    lat + count
  }

  def validCollectionTypes(set: Set[Geo], mission: Mission, map: Map[String, Int])(polygon: List[LatLon]): Geo = {
    println(s"validCollectionTypes: $sender, $set, $mission, $polygon, $map")
    val item = mission.head
    Geo(item.x, item.y, item.z)
  }

  def invalidType(num: Int, api: TestAPI): Unit = {
    println(s"invalidType")
  }

  def invalidCollectionType(num: Int, stack: Queue[Int]): Unit = {
    println(s"invalidCollectionType")
  }

  def duplicateActor(sender2: ActorRef, num: Int): Unit = {
    println(s"duplicateActor")
  }
}

class NoCtorAPI {
  def geo(lat: Double, lon: Double, alt: Double, sender: ActorRef = Actor.noSender): Geo = {
    println(s"geo: $lat, $lon, $alt")
    Geo(lat, lon, alt)
  }

  def noActor(num: Int, alt: Double): Unit = {
    println(s"noActor")
  }
}

object SingletonAPI {
  def echoName(name: String)(sender: ActorRef = Actor.noSender): String = {
    println(s"echoName: $name, $sender")
    name
  }

  def echoValue(echo: Boolean, value: Short, sender: ActorRef = Actor.noSender): Short = {
    println(s"echoValue: $echo, $value")
    if (echo) value else 0
  }
}
