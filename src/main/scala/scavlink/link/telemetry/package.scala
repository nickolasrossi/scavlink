package scavlink.link

import scavlink.state.{State, StateGenerator}

package object telemetry {
  type StateGenerators = Set[StateGenerator[_ <: State]]
}
