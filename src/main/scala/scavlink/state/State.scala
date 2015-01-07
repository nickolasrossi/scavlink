package scavlink.state

import scavlink.message.enums.MavDataStream
import scavlink.message.{VehicleId, Message}
import org.joda.time.DateTime

trait State {
  def vehicle: VehicleId
  val timestamp: DateTime = DateTime.now()
}

trait StateGenerator[S <: State] {
  def stateType: Class[S]
  def create: VehicleId => State
  def extract: StateExtractor
  def streams: Set[MavDataStream.Value]
  def messages: Set[Message]
  override def toString = stateType.getSimpleName
}
