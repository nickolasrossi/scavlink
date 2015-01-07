package scavlink.link.mission

import akka.actor.{ActorRef, ActorSystem, Props}
import scavlink.link.mission.MissionAskAPI._
import scavlink.link.operation.VehicleOpSpecSupport
import scavlink.message.common.MissionItem
import scavlink.message.{ComponentId, SystemId}

import scala.concurrent.duration.FiniteDuration

abstract class MissionOpSpec(_system: ActorSystem) extends VehicleOpSpecSupport(_system) with MissionTestData {
  val emptyMission: Mission = Vector.empty

  def setGCS(item: MissionItem): MissionItem = item.setTarget(SystemId.GroundControl, ComponentId.GroundControl)

  def withGetMission(testCode: ActorRef => Any): Unit =
    withActor(classOf[GetMissionActor])(testCode)

  def withSetMission(testCode: ActorRef => Any): Unit =
    withActor(classOf[SetMissionActor])(testCode)


  override def withResponder(props: Props)(testCode: => Unit): Unit = {
    vehicle.clearMissionCache()
    super.withResponder(props)(testCode)
  }

  def withBasicResponder(resultSet: Mission) =
    withResponder(Props(classOf[BasicMissionVehicle], events, resultSet)) _

  def withDuplicatingResponder(resultSet: Mission, every: Int) =
    withResponder(Props(classOf[DuplicatingMissionVehicle], events, resultSet, every)) _

  def withDroppingResponder(resultSet: Mission, every: Int) =
    withResponder(Props(classOf[DroppingMissionVehicle], events, resultSet, every)) _

  def withDelayingResponder(resultSet: Mission, every: Int, delay: FiniteDuration) =
    withResponder(Props(classOf[DelayingMissionVehicle], events, resultSet, every, delay)) _

  def withDyingResponder(resultSet: Mission, after: Int) =
    withResponder(Props(classOf[DyingMissionVehicle], events, resultSet, after)) _
}
