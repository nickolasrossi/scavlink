import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.concurrent.Worker.State

import scavlink.test.map.{FlightMap, SimFlight, Timer}

import scalafx.Includes._
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.scene.Scene
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import scalafx.scene.web._

object MapView extends JFXApp {
  val flight = SimFlight.Nothing
//  val flight = SimFlight.SunnyvaleTransects

  val browser = new WebView {
    hgrow = Priority.Always
    vgrow = Priority.Always
    onAlert = (e: WebEvent[_]) => println("onAlert: " + e)
    onResized = (e: WebEvent[_]) => println("onResized: " + e)
    onVisibilityChanged = (e: WebEvent[_]) => println("onVisibilityChanged: " + e)
    onStatusChanged = (e: WebEvent[_]) => println("onStatusChanged: " + e)
  }

  stage = new PrimaryStage {
    title = "Map"
    width = 800
    height = 600
    scene = new Scene {
      fill = Color.LightGray
      root = new BorderPane {
        hgrow = Priority.Always
        vgrow = Priority.Always
        center = browser
      }
    }
  }

  val startMap = new ChangeListener[State] {
    def changed(ov: ObservableValue[_ <: State], oldState: State, newState: State): Unit = {
      if (newState == State.SUCCEEDED) {
        val flightMap = new FlightMap(engine, flight)
        
        // since callback from javascript to JavaFX didn't work, give the map time to initialize
        Timer(2000, false) { Platform.runLater { flightMap.run() } }
      }
    }
  }

  val engine = browser.engine
  engine.javaScriptEnabled = true
  engine.getLoadWorker.stateProperty.addListener(startMap)
  engine.load(getClass.getResource("/map/map.html").toString)
}
