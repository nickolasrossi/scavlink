import sbt._
import Keys._
import scavlink.sbt.mavgen.MavGenPlugin

object ScavlinkBuild extends Build
{
  lazy val scavlink =
    Project("scavlink", file("."))
      .enablePlugins(MavGenPlugin)
      .configs( IntegrationTest )
      .settings( Defaults.itSettings : _*)
}
