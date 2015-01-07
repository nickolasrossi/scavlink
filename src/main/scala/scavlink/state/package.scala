package scavlink

import scavlink.message.Message

package object state {
  type StateExtractor = PartialFunction[(State, Message), State]

  val LatLonScale = scavlink.coord.LatLonScale
  val AltScale = 1000
}
