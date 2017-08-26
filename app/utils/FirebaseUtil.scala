package utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.tasks.Tasks;
import com.google.firebase.tasks.OnSuccessListener

object FirebaseUtil { 
  def createCustomToken(provider: String, firebaseId: String): Option[String] = {
    var firebaseToken:Option[String] = None
    if (provider == "stackexchange") {
      val fireBaseAuth = FirebaseAuth.getInstance()
      Tasks.await(fireBaseAuth.createCustomToken(firebaseId).addOnSuccessListener(
        new OnSuccessListener[String]() {
          override def onSuccess(token: String): Unit = {
            firebaseToken = Some(token)
          }              
        }
      ))
    }
    firebaseToken
  }
}
