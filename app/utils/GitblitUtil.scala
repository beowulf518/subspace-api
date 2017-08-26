package utils

import java.io.File

import scala.collection.JavaConverters.asScalaBufferConverter

import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit

import com.gitblit.models.RepositoryModel
import com.gitblit.models.UserModel
import com.gitblit.utils.JGitUtils
import com.gitblit.utils.RpcUtils
import com.google.inject.Inject
import com.google.inject.Singleton

import play.api.Configuration
import play.api.Logger

@Singleton
class GitblitUtil @Inject() (config: Configuration){
  val adminUsername = config.getString("gitblit.admin.username").get
  val adminPassword = config.getString("gitblit.admin.password").get
  val serverURL = config.getString("gitblit.serverURL").get
  val gitBaseDir = config.getString("gitblit.gitBaseDir").get

  def createUser(userName: String, password: String) = {
    val user: UserModel = new UserModel(userName)
    user.password = password
    user.canCreate = true
    user.canFork = true
    RpcUtils.createUser(user, serverURL, adminUsername, adminPassword.toCharArray())
  }

  def createRepository(name: String, description: String, userName: String) = {
    val repo: RepositoryModel = new RepositoryModel()
    repo.name = name
    repo.description = description
    repo.addOwner(userName)
    RpcUtils.createRepository(repo, serverURL, adminUsername, adminPassword.toCharArray())
  }

  def getRepository(name: String): Repository = {
    try {
      val gitDir = new File(gitBaseDir, name + ".git")
      new RepositoryBuilder().setGitDir(gitDir).build();
    } catch {
      case e: Throwable => {
        Logger.error("Get Gitblit Repo Exception", e)
        null
      }
    }
  }

  def getCommits(repositoryName: String, maxCount: Int): List[RevCommit] = {
    JGitUtils.getRevLog(getRepository(repositoryName), maxCount).asScala.toList
  }
}
