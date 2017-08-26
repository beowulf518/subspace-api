package dao.models

import java.sql.Timestamp

import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevBlob
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTree
import sangria.relay.Node
import utils.RandomGenerator

trait GitObject {
  val oid: ObjectId
}

trait Entity extends Node {
  val id: String
}

case class Viewer(
  id: String = "ViewerID"
) extends Entity

case class User(
  override val id: String,
  userName: String,
  fullName: Option[String],
  photoUrl: Option[String],
  emailAddress: Option[String],
  password: String,
  reputation: Double = 1.0,
  isInvisible: Boolean = false,
  createdAt: Option[Timestamp] = None,
  updatedAt: Option[Timestamp] = None
) extends Entity

case class UserProviderAccount(
  firebaseId: String,
  userId: String,
  provider: String,
  userName: String,
  providerId: Option[String],
  accessToken: Option[String],
  firebaseToken: Option[String],
  createdAt: Option[Timestamp] = None,
  updatedAt: Option[Timestamp] = None
) extends Entity {
  val id = firebaseId
}

case class Repository (
  override val id: String,
  name: String,
  ownerId: String,
  isPrivate: Boolean,
  isPushVote: Boolean,
  isReviewStash: Boolean,
  createdAt: Option[Timestamp] = None,
  updatedAt: Option[Timestamp] = None
) extends Entity

case class Project(
  id: String,
  name: String,
  ownerId: String,
  repositoryId: String,
  goals: Option[String] = None,
  description: Option[String] = None,
  createdAt: Option[Timestamp] = None,
  updatedAt: Option[Timestamp] = None
) extends Entity

case class Topic(id: String, createdAt: Option[Timestamp] = None) extends Entity
case class ProjectTopic(projectId: String, topicId: String, id: String = RandomGenerator.randomUUIDString) extends Entity

case class Ref(
  override val id: String,
  oid: ObjectId,
  name: String,
  prefix: String,
  repository: Repository
) extends Entity

case class GitActor(name: Option[String], email: Option[String], user: Option[User])

case class Commit(
  override val id: String,
  oid: ObjectId,
  author: GitActor,
  shortMessage: String,
  fullMessage: String,
  tree: Tree,
  commitTime: Long,
  refId: String,
  repository: Repository
) extends Entity with GitObject {
  def this(rev: RevCommit, refId: String, repo: Repository, user: Option[User]) = this(
    rev.getName,
    rev.getId,
    GitActor(Some(rev.getAuthorIdent.getName), Some(rev.getAuthorIdent.getEmailAddress), user),
    rev.getShortMessage,
    rev.getFullMessage,
    new Tree(rev.getTree, repo),
    rev.getCommitterIdent.getWhen.getTime/1000,
    refId,
    repo
  )
}

case class TreeEntry(mode: Int, name: String, oid: ObjectId, repository: Repository, `type`: String, `object`: Option[GitObject])

case class Tree(override val id: String, oid: ObjectId, repository: Repository) extends Entity with GitObject {
  def this(revTree: RevTree, repo: Repository) = this(revTree.getName, revTree.getId, repo)
}

case class Blob(override val id: String, oid: ObjectId, byteSize: Long, repository: Repository, text: Option[String]) extends Entity with GitObject {
  def this(rev: RevBlob, content: (Option[String], Long), repo: Repository) =
    this(rev.getName, rev.getId, content._2, repo, content._1)
}

case class DiffInfo(
  changeType: ChangeType,
  oldPath: String,
  newPath: String,
  oldObjectId: Option[String],
  newObjectId: String,
  oldMode: String,
  newMode: String,
  diff: String
)

case class Stash(
  override val id: String,
  ownerId: String,
  repositoryId: String,
  stashNum: Int,
  baseOid: String,
  currentOid: String,
  isMerged: Boolean,
  voteTreshold: Double,
  isOnline: Boolean,
  description: Option[String],
  title: Option[String],
  createdAt: Option[Timestamp] = None,
  updatedAt: Option[Timestamp] = None  
) extends Entity

case class StashComment(
  id: String,
  content: String,
  ownerId: String,
  stashId: String,
  parentId: Option[String],
  totalUpVotePoints: Double = 0,
  totalDownVotePoints: Double = 0,
  totalAllVotePoints: Double = 0,
  upVoteCount: Int = 0,
  downVoteCount: Int = 0,
  allVoteCount: Int = 0,
  createdAt: Option[Timestamp] = None,
  updatedAt: Option[Timestamp] = None
) extends Entity

case class StashVote(
  ownerId: String,
  stashId: String,
  votePoint: Double,
  isVoteUp: Boolean,
  createdAt: Option[Timestamp] = None,
  updatedAt: Option[Timestamp] = None
) extends Entity {
  val id = ownerId + stashId
}

case class StashCommentVote(
  ownerId: String,
  stashCommentId: String,
  votePoint: Double,
  isVoteUp: Boolean,
  createdAt: Option[Timestamp] = None,
  updatedAt: Option[Timestamp] = None
) extends Entity {
  val id = ownerId + stashCommentId
}
