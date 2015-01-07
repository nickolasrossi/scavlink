package scavlink.link.fence

import scavlink.coord.LatLon
import scavlink.settings.SettingsCompanion
import com.typesafe.config.Config
import spire.implicits._
import spire.math.Interval

import scala.collection.JavaConversions._

/**
 * Parses fences defined in configuration.
 * @author Nick Rossi
 */
object DefinedFences extends SettingsCompanion[Map[String, Fence]]("define") {

  def fromSubConfig(config: Config): Map[String, Fence] = {
    val configs = config.root.keySet.map { name =>
      name -> config.getConfig(name)
    }.toMap

    // parse unions last, since they reference fence names
    val (unionConfigs, fenceConfigs) = configs.partition(_._2.hasPath("union"))

    val fences = fenceConfigs.mapValues(parseFence)
    val unions = unionConfigs.mapValues(parseFenceUnion(fences))

    fences ++ unions
  }

  /**
   * Parse a fence from a configuration block:
   * {
   *   corners = [...] | circle = { lat, lon, radius } | wkt = "..." | (empty)
   *   altitude = "[lo, hi]" | max-altitude = hi
   * }
   */
  def parseFence(config: Config): Fence = {
    val altitude = if (config.hasPath("altitude")) {
      Interval(config.getString("altitude")).mapBounds(_.toDouble)
    } else if (config.hasPath("upper-altitude")) {
      Interval.atOrBelow(config.getDouble("upper-altitude"))
    } else if (config.hasPath("lower-altitude")) {
      Interval.atOrAbove(config.getDouble("lower-altitude"))
    } else {
      Interval.all[Double]
    }

    if (config.hasPath("corners")) {
      val corners = config.getDoubleList("corners")
      val array = new Array[LatLon](corners.size / 2)
      cfor(0)(_ < corners.size, _ + 2) { i =>
        array(i/2) = LatLon(corners.get(i), corners.get(i+1))
      }
      Fence.polygon(array, altitude)

    } else if (config.hasPath("circle")) {
      val cfg = config.getConfig("circle")
      val center = LatLon(cfg.getDouble("lat"), cfg.getDouble("lon"))
      Fence.circle(center, cfg.getDouble("radius"), altitude)

    } else if (config.hasPath("wkt")) {
      Fence.wkt(config.getString("wkt"), altitude)

    } else {
      Fence.world(altitude)
    }
  }

  /**
   * Parse a fence that is a union of other fences:
   * {
   *    union = [ "fence1", "fence2", ... ]
   * }
   */
  def parseFenceUnion(fences: Map[String, Fence])(config: Config): Fence = {
    val union = config.getStringList("union")
    var fence: Fence = fences(union(0))
    cfor(1)(_ < union.length, _ + 1) { i =>
      fence = fence | fences(union(i))
    }
    fence
  }
}
