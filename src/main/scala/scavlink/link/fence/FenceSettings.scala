package scavlink.link.fence

import scavlink.settings.SettingsCompanion
import com.typesafe.config.Config

case class FenceSettings(fences: Map[String, Fence], bindings: Set[FenceBinding], breachAction: FenceBreachAction.Value)

object FenceSettings extends SettingsCompanion[FenceSettings]("fence") {
  def fromSubConfig(config: Config): FenceSettings = {
    val fences = DefinedFences.option(config) getOrElse Map.empty[String, Fence]
    val fenceBindings = FenceBinding(fences, config)
    val action = FenceBreachAction.withName(config.getString("on-breach"))
    FenceSettings(fences, fenceBindings, action)
  }
}
