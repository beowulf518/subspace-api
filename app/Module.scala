import java.io.FileInputStream

import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.firebase.FirebaseOptions.Builder
import com.google.firebase.auth.FirebaseCredentials
import com.google.inject.{Provider, Inject, AbstractModule}
import play.api.{Configuration, Environment}
import utils.{FirebaseViewerAction, ViewerAction}

class Module extends AbstractModule {

  override def configure() = {

    bind(classOf[FirebaseApp]).toProvider(classOf[FirebaseAppProvider]).asEagerSingleton()
//    bind(classOf[AuthenticatedAction]).to(classOf[FirebaseAuthenticatedAction]).asEagerSingleton()
    bind(classOf[ViewerAction]).to(classOf[FirebaseViewerAction]).asEagerSingleton()
  }
}

class FirebaseAppProvider @Inject() (environment: Environment, config: Configuration) extends Provider[FirebaseApp] {

  override def get(): FirebaseApp = {

    if (!FirebaseApp.getApps().isEmpty()) {
      return FirebaseApp.getInstance()
    }

    val serviceAccount = new FileInputStream(environment.getFile(config.getString("firebase.serviceFileName").get + ".json"))
    val fireBaseOpt: FirebaseOptions = new Builder()
      .setCredential(FirebaseCredentials.fromCertificate(serviceAccount))
      .setDatabaseUrl(config.getString("firebase.databaseURL").get)
      .build()

    FirebaseApp.initializeApp(fireBaseOpt)
  }
}
