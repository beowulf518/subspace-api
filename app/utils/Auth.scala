package utils

import java.security.cert.X509Certificate

import scala.concurrent.Future
import scala.concurrent.Promise

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.google.firebase.tasks.OnFailureListener
import com.google.firebase.tasks.OnSuccessListener
import com.google.inject.Inject
import com.google.inject.Singleton

import akka.actor.ActorSystem
import dao.DAOContainer
import dao.models.User
import play.api.mvc.ActionBuilder
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import play.api.libs.ws._
import play.api.Configuration

import utils.FirebaseUtil

trait ViewerRequest[+A] extends Request[A] {
  val viewer: Option[User]
}

object ViewerRequest {

  def apply[A](v: Option[User], req: Request[A]) = new ViewerRequest[A] {
    def body = req.body
    def headers = req.headers
    def id = req.id
    def method = req.method
    def path = req.path
    def queryString = req.queryString
    def remoteAddress = req.remoteAddress
    def secure = req.secure
    def tags = req.tags
    def uri = req.uri
    def version = req.version
    def clientCertificateChain: Option[Seq[X509Certificate]] = req.clientCertificateChain

    override val viewer = v
  }
}

trait ViewerAction extends ActionBuilder[ViewerRequest]

@Singleton
class FirebaseViewerAction @Inject() (system: ActorSystem, allDAO: DAOContainer, config: Configuration, ws: WSClient) extends ViewerAction {

  import system.dispatcher
  import play.api.Logger
  import play.api.mvc.Results.Unauthorized

  override def invokeBlock[A](request: Request[A], block: (ViewerRequest[A] => Future[Result])): Future[Result] = {
    val resultPromise = Promise[Result]()

    val token = request.headers.get("fbase").getOrElse("")
    if (token != "") {
      val fireBaseAuth = FirebaseAuth.getInstance()
      fireBaseAuth
        .verifyIdToken(
          request.headers.get("fbase").getOrElse("")
        )
        .addOnSuccessListener(
          new OnSuccessListener[FirebaseToken] {
            override def onSuccess(decodedToken: FirebaseToken): Unit = {
              Logger.debug(s"Decoded Uid: ${decodedToken.getUid}") // TODO: send result depending on exception
              val uid = decodedToken.getUid
              val userOpt = AsyncUtils.await(allDAO.userDAO.getUserByFirebaseId(uid))
              Logger.debug(s"UserOpt: ${String.valueOf(userOpt)}")
              block(ViewerRequest(userOpt, request)) map resultPromise.success
            }
          }
        )
        .addOnFailureListener(
          new OnFailureListener {
            override def onFailure(e: Exception): Unit = {
              Logger.debug(s"Failed to authenticate using Firebase. Message: ${e.getMessage}") // TODO: send result depending on exception
              resultPromise.success(Unauthorized)
            }
          }
        )
    } else {
      val token = request.headers.get("stexch").getOrElse("")
      if (token != "") {
        val json: JsValue = Json.parse(token)
        val providerId = (json \ "providerId").as[String]
        val accessToken = (json \ "token").as[String]
        val firebaseId = "se-" + providerId
        val userOpt = AsyncUtils.await(allDAO.userDAO.getUserByProvider(providerId, "stackexchange"))
        userOpt match {
          case Some(user) => {
            AsyncUtils.await(allDAO.userDAO.getUserProviderAccount("stackexchange", None, Some(user.id))) match {
              case Some(userProvider) => {
                if (accessToken == userProvider.accessToken.get) {
                  val firebaseToken = FirebaseUtil.createCustomToken("stackexchange", firebaseId)
                  AsyncUtils.await(allDAO.userDAO.updateFirebaseToken(firebaseId, firebaseToken))
                  block(ViewerRequest(userOpt, request)) map resultPromise.success
                } else {
                  // check if token valid
                  val url = "https://api.stackexchange.com/2.2/me"
                  val stackexchangeApiKey = config.getString("stackexchange.oauth.key").get
                  try {
                    val resJson = AsyncUtils.await(ws.url(url).withQueryString(
                      ("access_token" -> accessToken),
                      ("key" -> stackexchangeApiKey),
                      ("site" -> "stackoverflow")
                    ).get()).json
                    val uid = (resJson \\ "user_id")(0).toString()
                    if (uid == providerId) {
                      val firebaseToken = FirebaseUtil.createCustomToken("stackexchange", firebaseId)
                      AsyncUtils.await(allDAO.userDAO.updateFirebaseToken(firebaseId, firebaseToken))
                      allDAO.userDAO.updateAccessToken(firebaseId, Some(accessToken))
                      block(ViewerRequest(userOpt, request)) map resultPromise.success
                    } else {
                      block(ViewerRequest(None, request)) map resultPromise.success
                    }
                  } catch {
                    case _: Exception => block(ViewerRequest(None, request)) map resultPromise.success
                  }
                }
              }
              case None => block(ViewerRequest(None, request)) map resultPromise.success
            }
          }
          case None => {
            block(ViewerRequest(None, request)) map resultPromise.success
          }
        }
      } else {
        block(ViewerRequest(None, request)) map resultPromise.success
      }
    }

    resultPromise.future
  }
}
