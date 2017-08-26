package dao.models

import java.sql.Timestamp

import com.google.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.MySQLDriver
import utils.RandomGenerator

import scala.reflect.{ClassTag, classTag}
import slick.lifted.ForeignKeyQuery

@Singleton
class DatabaseTables @Inject() (dbConfigProvider: DatabaseConfigProvider) {
  val dbConfig =  dbConfigProvider.get[MySQLDriver]

  import dbConfig.driver.api._
  import slick.lifted.{Rep, Tag}

  val staticCreatedAt = "created_at"
  val staticUpdatedAt = "updated_at"
  val staticName = "name"
  val staticOwnerId = "owner_id"
  val staticRepositoryId = "repository_id"
  val staticDescription = "description"

  abstract class BaseTable[E: ClassTag](tag: Tag, tableName: String) extends Table[E](tag, tableName) {
    val classOfEntity: Class[_] = classTag[E].runtimeClass
    def id: Rep[String] = column[String]("id", O.PrimaryKey)
    def createdAt: Rep[Option[Timestamp]] = column[Option[Timestamp]](staticCreatedAt)
    def updatedAt: Rep[Option[Timestamp]] = column[Option[Timestamp]](staticUpdatedAt)
  }

  class UserTable(tag: Tag) extends BaseTable[User](tag, "Users") {
    def userName: Rep[String] = column[String]("user_name")
    def fullName: Rep[Option[String]] = column[Option[String]]("full_name")
    def photoUrl: Rep[Option[String]] = column[Option[String]]("photo_url")
    def emailAddress: Rep[Option[String]] = column[Option[String]]("email_address")
    def password: Rep[String] = column[String]("password")
    def reputation: Rep[Double] = column[Double]("reputation")
    def isInvisible: Rep[Boolean] = column[Boolean]("is_invisible")

    def * = (id, userName, fullName, photoUrl, emailAddress, password, reputation, isInvisible, createdAt, updatedAt) <> ((User.apply _).tupled, User.unapply)
  }

  class UserProviderAccountTable(tag: Tag) extends BaseTable[UserProviderAccount](tag, "UserProviderAccounts") {
    def firebaseId: Rep[String] = column[String]("firebase_id")
    def userId: Rep[String] = column[String]("user_id")
    def provider: Rep[String] = column[String]("provider")
    def userName: Rep[String] = column[String]("user_name")
    def providerId: Rep[Option[String]] = column[Option[String]]("provider_id")
    def accessToken: Rep[Option[String]] = column[Option[String]]("access_token")
    def firebaseToken: Rep[Option[String]] = column[Option[String]]("firebase_token")

    def * = (firebaseId, userId, provider, userName, providerId, accessToken, firebaseToken, createdAt, updatedAt) <> ((UserProviderAccount.apply _).tupled, UserProviderAccount.unapply)
    def diagramIdFK: ForeignKeyQuery[UserTable, User] =
      foreignKey("userprovideraccount_ibfk_1", userId, users)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  class RepositoryTable(tag: Tag) extends BaseTable[Repository](tag, "Repositories") {
    def name: Rep[String] = column[String](staticName)
    def isPrivate: Rep[Boolean] = column[Boolean]("is_private")
    def isPushVote: Rep[Boolean] = column[Boolean]("is_push_vote")
    def ownerId: Rep[String] = column[String](staticOwnerId)
    def isReviewStash: Rep[Boolean] = column[Boolean]("is_review_stash")

    def * = (id, name, ownerId, isPrivate, isPushVote, isReviewStash, createdAt, updatedAt) <> ((Repository.apply _).tupled, Repository.unapply)

    def fkOwnerId: ForeignKeyQuery[UserTable, User] =
      foreignKey("fk_owner_id_users", ownerId, users)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  class ProjectTable(tag: Tag) extends BaseTable[Project](tag, "Projects") {
    def name: Rep[String] = column[String](staticName)
    def ownerId: Rep[String] = column[String](staticOwnerId)
    def repositoryId: Rep[String] = column[String](staticRepositoryId)
    def goals: Rep[Option[String]] = column[Option[String]]("goals")
    def description: Rep[Option[String]] = column[Option[String]](staticDescription)

    def * = (id, name, ownerId, repositoryId, goals, description, createdAt, updatedAt) <> ((Project.apply _).tupled, Project.unapply)
  }

  class TopicTable(tag: Tag) extends BaseTable[Topic](tag, "Topics") {
    def value: Rep[String] = column[String]("value")

    def * = (id, createdAt) <> ((Topic.apply _).tupled, Topic.unapply)
  }

  class ProjectTopicsTable(tag: Tag) extends BaseTable[ProjectTopic](tag, "ProjectTopics") {
    def projectId: Rep[String] = column[String]("project_id")
    def topicId: Rep[String] = column[String]("topic_id")

    def * = (projectId, topicId, id) <> ((ProjectTopic.apply _).tupled, ProjectTopic.unapply)

    def projectIdFK: ForeignKeyQuery[ProjectTable, Project] =
      foreignKey("ProjectTopicTable_ProjectId_FK", projectId, projects)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
    def topicIdFK: ForeignKeyQuery[TopicTable, Topic] =
      foreignKey("ProjectTopicTable_TopicId_FK", topicId, topics)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  class StashTable(tag: Tag) extends BaseTable[Stash](tag, "Stashes") {
    def repositoryId: Rep[String] = column[String](staticRepositoryId)
    def ownerId: Rep[String] = column[String]("owner_id");
    def stashNum: Rep[Int] = column[Int]("stash_num")
    def baseOid: Rep[String] = column[String]("base_oid")
    def currentOid: Rep[String] = column[String]("current_oid")
    def isMerged: Rep[Boolean] = column[Boolean]("is_merged")
    def voteTreshold: Rep[Double] = column[Double]("vote_treshold")
    def isOnline: Rep[Boolean] = column[Boolean]("is_online")
    def description: Rep[Option[String]] = column[Option[String]]("description")
    def title: Rep[Option[String]] = column[Option[String]]("title")

    def * = (id, ownerId, repositoryId, stashNum, baseOid, currentOid, isMerged, voteTreshold, isOnline, description, title, createdAt, updatedAt) <> ((Stash.apply _).tupled, Stash.unapply)

    def repositoryIdFK: ForeignKeyQuery[RepositoryTable, Repository] =
      foreignKey("stashes_ibfk_1", repositoryId, repos)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  class StashCommentsTable(tag: Tag) extends BaseTable[StashComment](tag, "StashComments") {
    def content: Rep[String] = column[String]("content")
    def ownerId: Rep[String] = column[String](staticOwnerId)
    def stashId: Rep[String] = column[String]("stash_id")
    def parentId: Rep[Option[String]] = column[Option[String]]("parent_id")
    def totalUpVotePoints: Rep[Double] = column[Double]("total_up_vote_points")
    def totalDownVotePoints: Rep[Double] = column[Double]("total_down_vote_points")
    def totalAllVotePoints: Rep[Double] = column[Double]("total_all_vote_points")
    def upVoteCount: Rep[Int] = column[Int]("up_vote_count")
    def downVoteCount: Rep[Int] = column[Int]("down_vote_count")
    def allVoteCount: Rep[Int] = column[Int]("all_vote_count")
    def * = (id, content, ownerId, stashId, parentId, totalUpVotePoints, totalDownVotePoints, totalAllVotePoints, upVoteCount, downVoteCount, allVoteCount, createdAt, updatedAt) <> ((StashComment.apply _).tupled, StashComment.unapply)

    def stashIdFK: ForeignKeyQuery[StashTable, Stash] =
      foreignKey("stashcomments_ibfk_1", stashId, stashes)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
    def ownerIdFK: ForeignKeyQuery[UserTable, User] =
      foreignKey("stashcomments_ibfk_2", ownerId, users)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  class StashVotesTable(tag: Tag) extends BaseTable[StashVote](tag, "StashVotes") {
    def ownerId: Rep[String] = column[String](staticOwnerId)
    def stashId: Rep[String] = column[String]("stash_id")
    def votePoint: Rep[Double] = column[Double]("vote_point")
    def isVoteUp: Rep[Boolean] = column[Boolean]("is_vote_up")
    def * = (ownerId, stashId, votePoint, isVoteUp, createdAt, updatedAt) <> ((StashVote.apply _).tupled, StashVote.unapply)

    def stashIdFK: ForeignKeyQuery[StashTable, Stash] =
      foreignKey("stashvotes_ibfk_1", stashId, stashes)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
    def ownerIdFK: ForeignKeyQuery[UserTable, User] =
      foreignKey("stashvotes_ibfk_2", ownerId, users)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  class StashCommentVotesTable(tag: Tag) extends BaseTable[StashCommentVote](tag, "StashCommentVotes") {
    def ownerId: Rep[String] = column[String](staticOwnerId)
    def stashCommentId: Rep[String] = column[String]("stash_comment_id")
    def votePoint: Rep[Double] = column[Double]("vote_point")
    def isVoteUp: Rep[Boolean] = column[Boolean]("is_vote_up")
    def * = (ownerId, stashCommentId, votePoint, isVoteUp, createdAt, updatedAt) <> ((StashCommentVote.apply _).tupled, StashCommentVote.unapply)

    def stashCommentIdFK: ForeignKeyQuery[StashCommentsTable, StashComment] =
      foreignKey("stashcommentvotes_ibfk_1", stashCommentId, stashComments)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
    def ownerIdFK: ForeignKeyQuery[UserTable, User] =
      foreignKey("stashcommentvotes_ibfk_2", ownerId, users)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  val users = TableQuery[UserTable]
  val userProviderAccounts = TableQuery[UserProviderAccountTable]
  val repos = TableQuery[RepositoryTable]
  val topics = TableQuery[TopicTable]
  val projects = TableQuery[ProjectTable]
  val projectTopics = TableQuery[ProjectTopicsTable]
  val stashes = TableQuery[StashTable]
  val stashComments = TableQuery[StashCommentsTable]
  val stashVotes = TableQuery[StashVotesTable]
  val stashCommentVotes = TableQuery[StashCommentVotesTable]
}
