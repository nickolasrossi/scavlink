package scavlink.link.parameter

import scavlink.link.operation.VehicleOpSpecSupport
import scavlink.link.parameter.types.ParameterResultSet
import akka.actor.{ActorRef, ActorSystem, Props}
import ParameterAskAPI._


import scala.concurrent.duration.FiniteDuration

abstract class ParameterOpSpec(_system: ActorSystem) extends VehicleOpSpecSupport(_system) with ParameterTestData {

  def withGetAllParameters(testCode: ActorRef => Any): Unit =
    withActor(classOf[GetAllParametersActor])(testCode)

  def withGetNamedParameters(testCode: ActorRef => Any): Unit =
    withActor(classOf[GetNamedParametersActor])(testCode)

  def withSetParameters(testCode: ActorRef => Any): Unit =
    withActor(classOf[SetParametersActor])(testCode)


  override def withResponder(props: Props)(testCode: => Unit): Unit = {
    vehicle.clearParameterCache()
    super.withResponder(props)(testCode)
  }

  def withBasicResponder(resultSet: ParameterResultSet) =
    withResponder(Props(classOf[BasicParameterVehicle], events, resultSet)) _

  def withDuplicatingResponder(resultSet: ParameterResultSet, every: Int) =
    withResponder(Props(classOf[DuplicatingParameterVehicle], events, resultSet, every)) _

  def withDroppingResponder(resultSet: ParameterResultSet, every: Int) =
    withResponder(Props(classOf[DroppingParameterVehicle], events, resultSet, every)) _

  def withDelayingResponder(resultSet: ParameterResultSet, every: Int, delay: FiniteDuration) =
    withResponder(Props(classOf[DelayingParameterVehicle], events, resultSet, every, delay)) _

  def withDyingResponder(resultSet: ParameterResultSet, after: Int) =
    withResponder(Props(classOf[DyingParameterVehicle], events, resultSet, after)) _
}
