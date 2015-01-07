package scavlink.connection.frame

import scavlink.connection.marshal.MessageMarshaller
import scavlink.message.Bundle
import scavlink.message.enums.MavAutopilot

/**
 * Different autopilots may add their own messages to the MAVLink protocol.
 * This factory maps autopilot types to the marshallers that handle their custom messages.
 */
object DefaultMarshallerFactory {
  val marshallers = Map[MavAutopilot.Value, MessageMarshaller](
    MavAutopilot.ASLUAV -> marshallerFor(Bundle.ASLUAV),
    MavAutopilot.ARDUPILOTMEGA -> marshallerFor(Bundle.ardupilotmega),
    MavAutopilot.AUTOQUAD -> marshallerFor(Bundle.autoquad),
    MavAutopilot.PIXHAWK -> marshallerFor(Bundle.pixhawk),
    MavAutopilot.PX4 -> marshallerFor(Bundle.pixhawk),
    MavAutopilot.SLUGS -> marshallerFor(Bundle.slugs)
  ).withDefaultValue(Bundle.common.marshaller)

  private def marshallerFor(bundle: Bundle) =
    Bundle.common.marshaller orElse bundle.marshaller

  def apply(autopilot: MavAutopilot.Value): MessageMarshaller = marshallers(autopilot)
}
