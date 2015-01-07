package scavlink.task

import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, TrustManagerFactory}

import com.typesafe.config.Config
import scavlink.settings.SettingsCompanion
import spray.io.{ClientSSLEngineProvider, ServerSSLEngineProvider}

import scala.util.Try

trait SslConfiguration {
  def sslSettings: SslSettings

  // if there is no SSLContext in scope implicitly the HttpServer uses the default SSLContext,
  // since we want non-default settings in this example we make a custom SSLContext available here
  implicit def sslContext: SSLContext = Try(sslSettings.context) getOrElse SSLContext.getDefault

  // if there is no ServerSSLEngineProvider in scope implicitly the HttpServer uses the default one,
  // since we want to explicitly enable cipher suites and protocols we make a custom ServerSSLEngineProvider
  // available here
  implicit def sslServerEngineProvider: ServerSSLEngineProvider = {
    ServerSSLEngineProvider { engine =>
      setProtocols(engine)
      engine
    }
  }

  implicit def sslClientEngineProvider: ClientSSLEngineProvider = {
    ClientSSLEngineProvider { engine =>
      setProtocols(engine)
      engine.setUseClientMode(true)
      engine
    }
  }

  private def setProtocols(engine: SSLEngine): Unit = {
    engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
    engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
  }
}


case class SslSettings(keyStoreName: String, keyStorePassword: String, keyPassword: String) {
  lazy val context: SSLContext = {
    val keyStore = KeyStore.getInstance("jks")
    val resource = Try(getClass.getResourceAsStream(keyStoreName)) getOrElse new FileInputStream(keyStoreName)
    keyStore.load(resource, keyStorePassword.toCharArray)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, keyPassword.toCharArray)

    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }
}

object SslSettings extends SettingsCompanion[SslSettings]("ssl") {
  def fromSubConfig(config: Config): SslSettings =
    SslSettings(
      config.getString("key-store"),
      config.getString("key-store-password"),
      config.getString("key-password")
    )
}