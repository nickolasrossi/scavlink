package scavlink.link.parameter

import scavlink.link.parameter.types.ParameterResultSet
import scavlink.link.{MockVehicle, SentPacket}
import scavlink.message.common.{ParamRequestList, ParamRequestRead, ParamSet, ParamValue}
import scavlink.message.enums.MavParamType
import scavlink.message._
import akka.actor.Actor.Receive


object types {
  type ParameterResultSet = Map[String, PV]
  case class PV(number: Float, valueType: MavParamType.Value)
}

/**
 * Simulates vehicle responses to all parameter-related requests.
 */
trait MockParameterProtocol {
  self: MockVehicle =>

  def initialResultSet: ParameterResultSet

  private var resultSet = initialResultSet
  private var resultCount = resultSet.size
  private var resultSeq = resultSet.view.toSeq

  def send(message: Message, targetSystem: SystemId, targetComponent: ComponentId): Unit =
    send(Packet(VehicleId.fromLink(address, targetSystem), targetSystem, targetComponent, message))

  def parameterHandler: Receive = {
    case SentPacket(Packet(from, ParamRequestList(targetSystem, targetComponent))) =>
      resultSet.toSeq.zipWithIndex foreach {
        case ((name, value), n) =>
          val pv = ParamValue(name, value.number, value.valueType, resultCount.toShort, n.toShort)
          send(Packet(VehicleId.fromLink(address, targetSystem), targetSystem, targetComponent, pv))
      }

    case SentPacket(Packet(from, ParamRequestRead(targetSystem, targetComponent, name, index))) =>
      index match {
        case -1 =>
          resultSet.get(name) match {
            case Some(value) =>
              val pv = ParamValue(name, value.number, value.valueType, resultCount.toShort, -1)
              send(Packet(VehicleId.fromLink(address, targetSystem), targetSystem, targetComponent, pv))

            case None => // no response for an unknown parameter name
          }

        case n =>
          val index = Unsigned(n)
          if (index < resultSeq.length) {
            val (name, value) = resultSeq(index)
            val pv = ParamValue(name, value.number, value.valueType, resultCount.toShort, index.toShort)
            send(Packet(VehicleId.fromLink(address, targetSystem), targetSystem, targetComponent, pv))
          }
      }

    case SentPacket(Packet(from, ParamSet(targetSystem, targetComponent, name, number, valueType))) =>
      resultSet.get(name) match {
        case Some(pv) =>
          resultSet += name -> pv.copy(number = number)
          resultCount = resultSet.size
          resultSeq = resultSet.view.toSeq
          val message = ParamValue(name, number, valueType, resultSet.size.toShort, -1)
          send(Packet(VehicleId.fromLink(address, targetSystem), targetSystem, targetComponent, message))

        case None => // no response for an unknown parameter name
      }
  }
}
