name := "scavlink"

organization := "net.creativepath"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.4"

scalacOptions ++= Seq("-feature")

resolvers += "Spray" at "http://repo.spray.io"

libraryDependencies ++= {
  val akkaV = "2.3.8"
  val sprayV = "1.3.2"
  val jsonV = "3.2.11"
  Seq(
    "com.typesafe.akka"   %% "akka-actor"               % akkaV,
    "com.typesafe.akka"   %% "akka-slf4j"               % akkaV,
    "com.typesafe"        %  "config"                   % "1.2.1",     // config
    "joda-time"           %  "joda-time"                % "2.5",       // datetime
    "org.joda"            %  "joda-convert"             % "1.7",       // datetime
    "org.slf4j"           %  "slf4j-api"                % "1.7.7",     // logging
    "ch.qos.logback"      %  "logback-classic"          % "1.1.2",     // logging
    "org.spire-math"      %% "spire"                    % "0.8.2",     // math implicits
    "com.spatial4j"       %  "spatial4j"                % "0.4.1",     // geodetic shapes on JTS
    "com.vividsolutions"  %  "jts"                      % "1.13",      // geometry
    "gov.nist.math"       %  "jama"                     % "1.0.3",     // Matrix pseudo-inverse in LeastSquaresDistance
    "org.bidib.jbidib"    %  "jbidibc-rxtx-2.2"         % "1.6.0",     // cross-platform serial port
    "org.bidib.jbidib"    %  "bidib-rxtx-binaries"      % "2.2",       // cross-platform serial port
    "com.github.scala-incubator.io" %% "scala-io-core"  % "0.4.3",     // for packet log reader
    "com.github.scala-incubator.io" %% "scala-io-file"  % "0.4.3",     // for packet log reader
    "io.dropwizard.metrics" % "metrics-core"            % "3.1.0",     // JMX/stat reporting
    "io.spray"            %% "spray-can"                % sprayV,      // http server
    "io.spray"            %% "spray-routing"            % sprayV,      // http server
    "io.spray"            %% "spray-caching"            % sprayV,      // http/token cache
    "io.spray"            %% "spray-client"             % sprayV,      // http client
    "com.wandoulabs.akka" %% "spray-websocket"          % "0.1.3",     // websockets
    "net.jpountz.lz4"     %  "lz4"                      % "1.3",       // http compression
    "org.json4s"          %% "json4s-jackson"           % jsonV,       // json
    "org.json4s"          %% "json4s-ext"               % jsonV,       // json
    "org.scalatest"       %% "scalatest"                % "2.2.1"      % "it,test",
    "com.typesafe.akka"   %% "akka-testkit"             % akkaV        % "it,test",
    "io.spray"            %% "spray-testkit"            % sprayV       % "it,test",
    "org.scalafx"         %% "scalafx"                  % "2.2.67-R10" % "it,test"    // embedded browser for maps
  )
}

compile in Compile <<= (compile in Compile).dependsOn(mavgenTask in Compile)

unmanagedJars in Test +=
  Attributed.blank(file(scala.util.Properties.javaHome) / "/lib/jfxrt.jar")

unmanagedJars in IntegrationTest +=
  Attributed.blank(file(scala.util.Properties.javaHome) / "/lib/jfxrt.jar")
