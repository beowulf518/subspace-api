package utils

import java.io.ByteArrayOutputStream
import java.io.File

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.mutable.ListBuffer

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId

import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.revwalk.RevBlob
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevObject
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter

import com.google.inject.Inject
import com.google.inject.Singleton

import SyntaxSugars.using
import dao.DAOContainer
import dao.RepositoryDAO
import dao.models.Blob
import dao.models.Commit
import dao.models.DiffInfo
import dao.models.Ref
import dao.models.Tree
import dao.models.TreeEntry
import play.api.Configuration
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json
import org.eclipse.jgit.diff.DiffEntry
import sangria.execution.UserFacingError


@Singleton
class JGitUtil @Inject() (config: Configuration, allDao: DAOContainer, repoDao: RepositoryDAO){

  val gitBaseDir = config.getString("gitblit.gitBaseDir").get
  val gitServerUrl = config.getString("gitblit.serverURL").get
  val gitClientUrl = config.getString("gitblit.clientUrl").get

  case class AlreadyExistsError(message: String) extends Exception(message) with UserFacingError

  private def getOwnerDirPath(username: String) = s"$gitBaseDir/~$username" // user directory is prefixed by tilde

  /**
   * Convert dao.models.Repository into org.eclipse.jgit.lib.Repository
   *
   * @param repo base repository
   * @return org.eclipse.jgit.lib.Repository
   */
  private def getJgitRepository(repo: dao.models.Repository): Repository = {
    val userName = AsyncUtils.await(allDao.userDAO.getById(repo.ownerId)) map(_.userName) getOrElse ""
    val ownerDirPath = getOwnerDirPath(userName)
    val gitDir = new File(ownerDirPath, repo.name + ".git")
    new RepositoryBuilder().setGitDir(gitDir).build()
  }

  /**
   * Create a new repository
   *
   * @param userName a repository creator userName
   * @param repoName repository Name
   * @return Unit
   */
  def initRepository(userName: String, repoName: String): Unit = {
    val ownerDirPath = getOwnerDirPath(userName)
    val repoPath = s"$ownerDirPath/$repoName.git"

    // create directories if not exists
    new File(ownerDirPath) mkdirs()

    try {
      val repo = new FileRepositoryBuilder() setGitDir new File(repoPath) readEnvironment() build()
      repo.create(true)
    } catch {
      case e: Exception => throw AlreadyExistsError("Repo with this name already exists")
    }
  }

  /**
   * Get an URL pointing to repository URL address on git server
   *
   * @param repo base repository
   * @return URL String
   */
  def getRepositoryUrl(repo: dao.models.Repository): String = {
    val userName = AsyncUtils.await(allDao.userDAO.getById(repo.ownerId)) map(_.userName) getOrElse ""
    s"$gitClientUrl/r/~$userName/${repo.name}.git"
  }

  /**
   * Get list of TreeEntry (Tree/Folder or Blob/File Object). List objects inside a folder/tree.
   *
   * @param repo base repository
   * @param objectId the base Tree/Folder object where the tree start
   * @param path if set, filter the result to only include result from path
   * @return List of objects inside a tree
   */
  def getTreeEntries(repo: dao.models.Repository, objectId: ObjectId, path: Option[String]): List[TreeEntry] = {
    val treeWalk = new TreeWalk(getJgitRepository(repo))
    treeWalk.addTree(objectId)
    treeWalk.setRecursive(false)

    if (path.exists(_.trim.nonEmpty)) {
      treeWalk.setFilter(PathFilter.create(path.get))
    }

    var res = ListBuffer[TreeEntry]()
    while (treeWalk.next()) {
      if (path.exists(_.trim.nonEmpty) && path.get.contains(treeWalk.getPathString) && treeWalk.isSubtree()) {
        treeWalk.enterSubtree()
      } else {
        val revObject = getRevObjectFromId(repo, treeWalk.getObjectId(0))
        val revObj = if (revObject.getType == Constants.OBJ_TREE) {
          Some(Tree(revObject.getName, revObject.getId, repo))
        } else if (revObject.getType == Constants.OBJ_BLOB) {
          val content = getContentFromId(repo, revObject.getId, false)
          Some(new Blob(revObject.asInstanceOf[RevBlob], content, repo))
        } else {
          None
        }
        res += TreeEntry(
          treeWalk.getFileMode.toString().toInt,
          treeWalk.getPathString,
          treeWalk.getObjectId(0),
          repo,
          Constants.typeString(treeWalk.getFileMode.getObjectType),
          revObj
        )
      }
    }
    treeWalk.close()
    res.toList
  }

  /**
   * Convert a git object into Commit, Tree, Blob, or Tag Type
   *
   * @param repo base repository
   * @param objectId the object to be converted
   * @return Converted Object
   */
  def getRevObjectFromId(repo: dao.models.Repository, objectId: ObjectId): RevObject = {
    val revWalk = new RevWalk(getJgitRepository(repo))
    val revObject: RevObject = revWalk.parseAny(objectId) match {
      case r: RevTag => revWalk.parseCommit(r.getObject)
      case r: RevTree => revWalk.parseTree(objectId)
      case r: RevBlob => revWalk.lookupBlob(objectId)
      case _         => revWalk.parseCommit(objectId)
    }
    revWalk.dispose
    revObject
  }

  /**
   * Construct a Ref (Branch / Stash) ID
   *
   * @param repoId base repository ID
   * @param refName branch or stash name
   * @return A String represent a ref ID
   */
  def getRefId(repoId: String, refName: String): String =
    Json.stringify(JsObject(Seq(
      "repoId" -> JsString(repoId),
      "refName" -> JsString(refName)
    )))

  /**
   * Find a ref (Branch / Stash) by ID
   *
   * @param RefId
   * @return Ref Object
   */
  def getRefById(RefId: String): Option[Ref] = {
    val refObj = Json.parse(RefId)
    val refName = (refObj \ "refName").as[String]
    val repo = repoDao.find((refObj \ "repoId").as[String]).get
    getRepositoryRef(repo, refName)
  }

  /**
   * Search for a ref by (possibly abbreviated) name.
   *
   * @param name
   *            the name of the ref to lookup. May be a short-hand form, e.g.
   *            "master" which is is automatically expanded to
   *            "refs/heads/master" if "refs/heads/master" already exists.
   * @return Ref
   */
  def getRepositoryRef(repo: dao.models.Repository, name: String): Option[Ref] = {
    val ref = getJgitRepository(repo).findRef(name)
    if (ref != null) {
      Some(Ref(getRefId(repo.id, ref.getName), ref.getObjectId, ref.getName, ref.getName, repo))
    } else {
      None
    }
  }

  /**
   * Search for a ref by (possibly abbreviated) name.
   *
   * @param name
   *            the name of the ref to lookup. May be a short-hand form, e.g.
   *            "master" which is is automatically expanded to
   *            "refs/heads/master" if "refs/heads/master" already exists.
   * @return Ref
   */
  def getRepositoryRefs(repo: dao.models.Repository, prefix: Option[String], isStash: Boolean): Seq[Ref] = {
    // TODO: Use regex for more precise pattern matching
    val allRefs = getJgitRepository(repo).getAllRefs.map(ref => {
      Ref(getRefId(repo.id, ref._2.getName), ref._2.getObjectId, ref._1, ref._1, repo)
    }).toSeq.filterNot(ref => (ref.name.startsWith("refs/meta")) || (ref.name == "HEAD"))
    (prefix match {
      case Some(p) => allRefs.filter(_.name.startsWith(p))
      case None => allRefs
    }).filter(ref => if (isStash) ref.name.contains("stash-") else !(ref.name.contains("stash-")))
  }

  private def getLfsObjects(text: String): Map[String, String] = {
    if(text.startsWith("version https://git-lfs.github.com/spec/v1")){
      // LFS objects
      text.split("\n").map { line =>
        val dim = line.split(" ")
        dim(0) -> dim(1)
      }.toMap
    } else {
      Map.empty
    }
  }

  private def getContentSize(loader: ObjectLoader): Long = {
    if(loader.isLarge) {
      loader.getSize
    } else {
      val bytes = loader.getCachedBytes
      val text = new String(bytes, "UTF-8")

      val attr = getLfsObjects(text)
      attr.get("size") match {
        case Some(size) => size.toLong
        case None => loader.getSize
      }
    }
  }

  /**
   * Get File Content by its object (Blob)
   *
   * @param repo base repository
   * @param id object ID (RevBlob) to be read
   * @param fetchLargeFile
   * @return A tuple of String content and its Long byte size (content, size)
   */
  def getContentFromId(repo: dao.models.Repository, id: ObjectId, fetchLargeFile: Boolean): (Option[String], Long) = try {
    using(getJgitRepository(repo).getObjectDatabase){ db =>
      val loader = db.open(id)
      val size = getContentSize(loader)
      if(loader.isLarge || (fetchLargeFile == false && FileUtil.isLarge(loader.getSize))){
        (None, size)
      } else {
        val out = new StringOutputStream
        loader.copyTo(out)
        (Some(out.s), size)
      }
    }
  } catch {
    case e: MissingObjectException => (None, 0)
  }

  /**
   * Get Commit object by Id
   *
   * @param repo base repository
   * @param refId branch or stash ID
   * @param commitId commit ID
   * @return Commit objects
   */
  def getCommit(repo: dao.models.Repository, refId: String, commitId: String): Option[Commit] = {
    val commitObj = getJgitRepository(repo).resolve(commitId)
    if (commitObj != null) {
      val revCommit = getRevObjectFromId(repo, commitObj).asInstanceOf[RevCommit]
      val user = AsyncUtils.await(allDao.userDAO.getById(repo.ownerId))
      Some(new Commit(revCommit, refId, repo, user))
    } else {
      None
    }
  }

  /**
   * Get commit history
   *
   * @param repo base repository
   * @param refName branch or stash name
   * @param path if not None, search history for specific file under this path
   * @param range if not None, filter history between 2 commit object id
   * @return RevCommit objects
   */
  def getCommitHistory(repo: dao.models.Repository, refName: String, path: Option[String] = None, range: Option[(String, String)] = None): Seq[RevCommit] = {
    val git = new Git(getJgitRepository(repo))
    val ref = git.getRepository.findRef(refName).getObjectId
    val log = git.log.add(ref)
    path match {
      case Some(objPath) => log.addPath(objPath)
      case None =>
    }
    range match {
      case Some((since, until)) => log.addRange(git.getRepository.resolve(since), git.getRepository.resolve(until))
      case None =>
    }
    log.call().toSeq.filter(_.getFullMessage != "push")
  }

  /**
   * Merge a stash to its base
   *
   * @param repo base repository
   * @param stashName stash name
   * @return Unit
   */
  def mergeStash(repo: dao.models.Repository, stashName: String): Unit = {
    val jgitRepo = getJgitRepository(repo)
    val checkStash = jgitRepo.findRef(stashName)
    if (checkStash != null) {
      val userName = AsyncUtils.await(allDao.userDAO.getById(repo.ownerId)) map (_.userName) getOrElse ""

      val destPath = s"$gitBaseDir/~system/$userName/${repo.name}.git"
      val repoUri = s"$gitServerUrl/r/~$userName/${repo.name}.git"

      val localPath = File.createTempFile(destPath, "")
      localPath.delete()

      val git = Git.cloneRepository()
        .setBare(false)
        .setURI(repoUri)
        .setDirectory(localPath)
        .call()

      val shortStashName = stashName.replaceAll("refs/heads/", "")
      val stashRefName = s"refs/remotes/origin/$shortStashName"
      val stashRef = git.getRepository.findRef(stashRefName).getObjectId
      val res = git.merge().include(stashRef).call()

      val stashNum = shortStashName.stripPrefix("stash-").toInt
      allDao.stashDAO.setMerged(repo.id, stashNum, true)

      val credential = new UsernamePasswordCredentialsProvider("admin", "admin")
      git.push().setCredentialsProvider(credential).call()

      localPath.delete()
    }
  }

  /**
   * Lists diff info between two revisions
   *
   * @param repo base repository
   * @param from argument to compare diff
   * @param to argument to compare diff
   * @return diff info objects
   */
  def getDiffs(repo: dao.models.Repository, from: Option[String], to: String): Seq[DiffInfo] = {
    val git = new Git(getJgitRepository(repo))
    val reader = git.getRepository.newObjectReader()

    val oldTreeIter = new CanonicalTreeParser()
    from match {
      case Some(fromTree) => oldTreeIter.reset(reader, git.getRepository.resolve(fromTree + "^{tree}"))
      case None =>
    }

    val newTreeIter = new CanonicalTreeParser()
    newTreeIter.reset(reader, git.getRepository.resolve(to + "^{tree}"))

    val byteArrayOutputStream = new ByteArrayOutputStream()
    val diffFormatter = new DiffFormatter(byteArrayOutputStream)

    val diffInfos = git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call() map { diff =>
      diffFormatter.setRepository(git.getRepository)
      diffFormatter.format(diff)

      val diffInfo = DiffInfo(
        changeType  = diff.getChangeType,
        oldPath     = diff.getOldPath,
        newPath     = diff.getNewPath,
        oldObjectId = if (from.isEmpty) None else Some(diff.getOldId.toString),
        newObjectId = diff.getNewId.toString,
        oldMode     = diff.getOldMode.toString,
        newMode     = diff.getNewMode.toString,
        diff        = byteArrayOutputStream.toString
      )

      byteArrayOutputStream.reset()

      diffInfo
    } toSeq

    byteArrayOutputStream.close()
    diffFormatter.close()

    diffInfos
  }

  /**
   * Get diff info from commit object
   *
   * @param repo base repository
   * @param commitOid commit objectId
   * @return diff info objects
   */
  def getDiffs(repo: dao.models.Repository, commitOid: ObjectId): Seq[DiffInfo] = {
    // TODO: Handle array of parents - merge.
    val git = new Git(getJgitRepository(repo))
    val revCommit = getRevObjectFromId(repo, commitOid).asInstanceOf[RevCommit]
    if (revCommit.getParentCount > 0) {
      getDiffs(repo, Some(revCommit.getParent(0).name), commitOid.name)
    } else {
      getDiffs(repo, None, commitOid.name)
    }
  }
}
