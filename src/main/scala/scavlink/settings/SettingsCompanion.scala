package scavlink.settings

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

/**
 * Helper class for extracting settings into case classes.
 * Design adapted from spray.io:
 * @see [[https://github.com/spray/spray/blob/master/spray-util/src/main/scala/spray/util/SettingsCompanion.scala]]
 */
abstract class SettingsCompanion[T](val prefix: String) {
  def apply(config: Config): T = fromSubConfig(config.getConfig(prefix))

  def option(config: Config): Option[T] = if (config.hasPath(prefix)) Some(apply(config)) else None
  
  def fromSubConfig(config: Config): T

  def getDuration(node: Config, name: String) =
    FiniteDuration(node.getDuration(name, TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
}
