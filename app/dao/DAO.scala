package dao

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.classTag

import com.google.inject.Inject
import com.google.inject.Singleton

import akka.actor.ActorSystem
import scala.concurrent.Await
import scala.concurrent.duration._
import dao.models.UserProviderAccount
import slick.profile.SqlUtilsComponent

import utils.MaybeFilter;

// TODO: split into separate files
trait DAO {
  def find(id: String): Future[Option[models.Entity]]
  def all: Future[Seq[models.Entity]]
  def paginate(start:Int, end: Int): Future[Seq[models.Entity]]
}

class DAOContainer @Inject() (val dbTables: models.DatabaseTables, system: ActorSystem) {
  import dbTables._
  import dbTables.dbConfig.db
  import dbTables.dbConfig.driver.api._
  import system.dispatcher
  import slick.lifted.{ CanBeQueryCondition, Rep, TableQuery }

  trait BaseDAOComponent[T <: BaseTable[E], E <: models.Entity] {
    def create(row: E) : Future[E]
    def getById(id: String) : Future[Option[E]]
    def getAll : Future[Seq[E]]
    def where(expression: Query[T, E, Seq]): Future[Seq[E]]
    def deleteById(id: String) : Future[Int]
    def updateById(id: String, row: E) : Future[Int]
  }

  trait BaseDAOQuery[T <: BaseTable[E], E <: models.Entity] {
    val query: dbConfig.driver.api.type#TableQuery[T]

    def createQuery(row: E) = {
      query returning query += row
    }

    def getByIdQuery(id: String) = {
      query.filter(_.id === id)
    }

    def getAllQuery = {
      query
    }

    def deleteByIdQuery(id: String) = {
      query.filter(_.id === id).delete
    }

    def updateByIdQuery(id: String, row: E) = {
      query.filter(_.id === id).update(row)
    }

    def toRepBoolean(criteriaSeq: Seq[Option[Rep[Boolean]]]): Rep[Boolean] = {
      criteriaSeq.collect({ case Some(criteria) => criteria }).reduceLeftOption(_ || _).getOrElse(true: Rep[Boolean])
    }
  }

  abstract class BaseDAO[T <: BaseTable[E], E <: models.Entity : ClassTag](clazz: TableQuery[T]) extends BaseDAOQuery[T, E] with BaseDAOComponent[T,E] {
    val clazzTable: TableQuery[T] = clazz
    lazy val clazzEntity = classTag[E].runtimeClass
    val query: dbConfig.driver.api.type#TableQuery[T] = clazz

    def create(row: E) = {
      db.run(query += row) map (_ => row)
    }

    def getById(id: String): Future[Option[E]] = {
      db.run(getByIdQuery(id).result.headOption)
    }

    def getAll: Future[Seq[E]] = {
      db.run(getAllQuery.result)
    }

    def where(expression: Query[T, E, Seq]) = {
      db run(expression result)
    }

    def updateById(id: String, row: E) = {
      db.run(updateByIdQuery(id, row))
    }

    def deleteById(id: String) = {
      db.run(deleteByIdQuery(id))
    }
  }

  @Singleton
  class UserDAO extends BaseDAO[dbTables.UserTable, models.User](TableQuery[dbTables.UserTable]) {
    def filterQuery(
      id: Option[String] = None,
      login: Option[String] = None,
      email: Option[String] = None
    ): Query[UserTable, UserTable#TableElementType, Seq] = {
      users.filter { item => toRepBoolean(
        Seq(
          id.map(item.id === _),
          email.map(item.emailAddress.get === _),
          login.map(login => item.userName.like(s"%${login}%"))
        ))
      }
    }

    def getUserById(id: String): Future[Seq[models.User]] = {
      db.run(users.filter(_.id === id) result)
    }

    def getUserByFirebaseId(firebaseId: String): Future[Option[models.User]] = {
      val filterFirebaseQuery = userProviderAccounts.filter(f => f.firebaseId === firebaseId)
      val joinQuery = users join filterFirebaseQuery on (_.id === _.userId)
      db.run((for {
        (c, s) <- joinQuery
      } yield (c)).result.headOption)
    }
    
    def getUserByProvider(providerId: String, provider: String): Future[Option[models.User]] = {
      val filterProviderQuery = userProviderAccounts.filter(f => f.providerId === providerId && f.provider === provider)
      val joinQuery = users join filterProviderQuery on (_.id === _.userId)
      db.run((for {
        (c, s) <- joinQuery
      } yield (c)).result.headOption)
    }

    def getByUserName(userName: String): Future[Seq[models.User]] = {
      db.run(users.filter(_.userName === userName) result)
    }

    def getUserName(userName: String): Future[Option[models.User]] = {
      db.run(users.filter(_.userName === userName).result.headOption)
    }

    def getByEmail(email: String): Future[Seq[models.User]] = {
      db.run(users.filter(_.emailAddress === email) result)
    }

    def updateIsInvisible(userId: String, isInvisible: Boolean): Future[Int] = {
      val q = for { c <- users if (c.id === userId) } yield c.isInvisible
      db.run(q.update(isInvisible))
    }
    
    def updateAccessToken(firebaseId: String, accessToken: Option[String]): Future[Int] = {
      val q = for { c <- userProviderAccounts if (c.firebaseId === firebaseId) } yield c.accessToken
      db.run(q.update(accessToken))
    }

    def updateFirebaseToken(firebaseId: String, firebaseToken: Option[String]): Future[Int] = {
      val q = for { c <- userProviderAccounts if (c.firebaseId === firebaseId) } yield c.firebaseToken
      db.run(q.update(firebaseToken))
    }
    
    def getAllUserProviderAccount(userId: String): Future[Seq[UserProviderAccount]] = {
      db.run(userProviderAccounts.filter(_.userId === userId).result)
    }

    def getUserProviderAccount(provider: String, providerId: Option[String], userId: Option[String] = None): Future[Option[UserProviderAccount]] = {
      val q = userId match {
        case Some(id) => userProviderAccounts.filter(s => (s.provider === provider) && (s.userId === id))
        case None => userProviderAccounts.filter(s => (s.providerId === providerId) && (s.provider === provider))
      }
      db.run(q.result.headOption)
    }

    def getUserProviderAccountByFirebaseId(firebaseId: String): Future[Option[UserProviderAccount]] = {
      db.run(userProviderAccounts.filter(_.firebaseId === firebaseId).result.headOption)
    }
    
    def insertUserProvider(userProvider: UserProviderAccount): Future[Int] = {
      db.run(userProviderAccounts += userProvider)
    }
  }

  @Singleton
  class ProjectDAO extends BaseDAO[dbTables.ProjectTable, models.Project](TableQuery[dbTables.ProjectTable]) {
    def filterQuery(
       id: Option[String] = None,
       repositoryId: Option[String] = None
     ): Query[ProjectTable, ProjectTable#TableElementType, Seq] = {
      projects.filter { item => toRepBoolean(
        Seq(
          id.map(item.id === _),
          repositoryId.map(item.repositoryId === _)
        ))
      }
    }

    def addTopics(projectId: String, topicIds: Seq[String]): Future[Option[models.Project]] = {
      db.run(projectTopics filter (_.projectId === projectId) result) flatMap { pTopics =>
        val newProjectTopics = topicIds filterNot {
          topicId => pTopics.exists(pTopic => pTopic.topicId == topicId)
        } map {t => models.ProjectTopic(projectId, t)}

        db.run((projectTopics ++= newProjectTopics).transactionally) flatMap  { _ =>
          getById(projectId)
        }
      }
    }

    def listTopics(projectId: String): Future[Seq[models.Topic]] = {
      val query = for {
        tagId <- projectTopics filter (_.projectId === projectId) map (_.topicId)
        tag <- topics filter (_.id === tagId)
      } yield tag

      db.run(query result)
    }
  }

  @Singleton
  class TopicDAO extends BaseDAO[dbTables.TopicTable, models.Topic](TableQuery[dbTables.TopicTable]) {
    def filterQuery(
      projectId: Option[String] = None
    ): Query[TopicTable, TopicTable#TableElementType, Seq] = {
      projectId match {
        case Some(id) => {
          for {
            pt <- projectTopics filter(_.projectId === projectId)
            topic <- topics filter(_.id === pt.topicId)
          } yield topic
        }
        case _ => topics
      }
    }

    def createMultiple(values: Seq[String]) = {
      val toBeInserted = values.map { value =>
        TableQuery[dbTables.TopicTable].insertOrUpdate(dao.models.Topic(value))
      }

      db.run(DBIO.sequence(toBeInserted))
    }

    def listProjects(topicId: String): Future[Seq[models.Project]] = {
      val query = for {
        pt <- projectTopics filter(_.topicId === topicId)
        project <- projects filter(_.id === pt.projectId)
      } yield project

      db.run(query result)
    }
  }

  @Singleton
  class StashDAO extends BaseDAO[dbTables.StashTable, models.Stash](TableQuery[dbTables.StashTable]) {
    def getAll(repositoryId: String, isOnline: Option[Boolean] = None): Future[Seq[models.Stash]] = {
      val q = stashes.filter(s => (s.repositoryId === repositoryId) && (s.isOnline === isOnline.getOrElse(true)))
      db.run(q.result)
    }

    def getAllByUserId(ownerId: String, isOnline: Option[Boolean] = None): Future[Seq[models.Stash]] = {
      val q = stashes.filter(s => (s.ownerId === ownerId) && (s.isOnline === isOnline.getOrElse(true)))
      db.run(q.result)
    }
    
    def getByRepo(repositoryId: String, stashNum: Int): Future[Option[models.Stash]] = {
      val q = stashes.filter(s => (s.repositoryId === repositoryId) && (s.stashNum === stashNum))
      db.run(q.result.headOption)
    }

    def setMerged(repositoryId: String, stashNum: Int, isMerged: Boolean): Future[Int] = {
      val q = stashes.filter(s => (s.repositoryId === repositoryId) && (s.stashNum === stashNum)).map(_.isMerged).update(true)
      db.run(q)
    }

    def updateIsOnline(stashId: String, isOnline: Boolean): Future[Int] = {
      val q = for { c <- stashes if (c.id === stashId) } yield c.isOnline
      db.run(q.update(isOnline))
    }    

    def updateMeta(stashId: String, title: Option[String], description: Option[String]): Future[Int] = {
      if (title.isEmpty && description.isEmpty) {
        Future(0)
      } else {
        (title, description) match {
          case (Some(titleVal), Some(descVal)) => {
            db.run(
              (for { c <- stashes if (c.id === stashId) } yield (c.title, c.description))
              .update((title, description))
            )
          }
          case (Some(titleVal), None) => {
            db.run(
              (for { c <- stashes if (c.id === stashId) } yield c.title)
              .update(title)
            )
          }
          case (None, Some(descVal)) => {
            db.run(
              (for { c <- stashes if (c.id === stashId) } yield c.description)
              .update(description)
            )
          }
          case (None, None) => {
            Future(0)
          }
        }
      }
    }
    
    def getAllCommentsCount(stashId: String): Future[Int] = {
      db.run(stashComments.filter(f => f.stashId === stashId).length.result)
    }

    private def commentsSortBy(q: Query[StashCommentsTable, dao.models.StashComment, Seq], sortBy: Option[String]) = {
      sortBy match {
        case Some(sorter) => {
          if (sorter == "popular") {
            q.sortBy(s => (s.allVoteCount.desc, s.createdAt.desc))
          } else if (sorter == "vote") {
            q.sortBy(s => (s.totalAllVotePoints.desc, s.createdAt.desc))
          } else if (sorter == "newest") {
            q.sortBy(s => s.createdAt.desc)
          } else {
            q.sortBy(s => s.createdAt)
          }
        }
        case None => q.sortBy(s => s.allVoteCount.desc)
      }
    }

    def getParentComments(stashId: String, sortBy: Option[String]): Future[Seq[models.StashComment]] = {
      val baseQ = stashComments.filter(f => f.stashId === stashId && f.parentId.isEmpty)
      val q = commentsSortBy(baseQ, sortBy)
      db.run(q.result)
    }

    def getChildComments(parentId: String, sortBy: Option[String]): Future[Seq[models.StashComment]] = {
      val baseQ = stashComments.filter(f => f.parentId === parentId)
      val q = commentsSortBy(baseQ, sortBy)
      db.run(q.result)
    }

    def insertComment(comment: models.StashComment): Future[Int] = {
      db.run(stashComments += comment)
    }

    def getVoteByUser(stashId: String, userId: String): Future[Option[models.StashVote]] = {
      db.run(stashVotes.filter(f => f.stashId === stashId && f.ownerId === userId).result.headOption)
    }

    def getVotes(stashId: String): Future[Seq[models.StashVote]] = {
      db.run(stashVotes.filter(f => f.stashId === stashId).result)
    }

    def getAcceptVotes(stashId: String): Future[Seq[models.StashVote]] = {
      db.run(stashVotes.filter(f => f.stashId === stashId && f.isVoteUp === true).result)
    }

    def getRejectVotes(stashId: String): Future[Seq[models.StashVote]] = {
      db.run(stashVotes.filter(f => f.stashId === stashId && f.isVoteUp === false).result)
    }

    def insertVote(vote: models.StashVote): Future[Int] = {
      db.run(stashVotes += vote)
    }

    def deleteVote(stashId: String, userId: String): Future[Int] = {
      db.run(stashVotes.filter(f => f.stashId === stashId && f.ownerId === userId).delete)
    }

    def updateVote(stashId: String, ownerId: String, isVoteUp: Boolean): Future[Int] = {
      val q = for { l <- stashVotes if (l.stashId === stashId) && (l.ownerId === ownerId) } yield l.isVoteUp
      db.run(q.update(isVoteUp))
    }

    def getCommentVoteByUser(stashCommentId: String, userId: String): Future[Option[models.StashCommentVote]] = {
      db.run(stashCommentVotes.filter(f => f.stashCommentId === stashCommentId && f.ownerId === userId).result.headOption)
    }

    def insertCommentVote(vote: models.StashCommentVote): Future[Unit] = {
      // Slick currently not support increment https://github.com/slick/slick/issues/497
      // It's better to update the count on insert or update rather than recounting it on every select
      val a = (for {
        _ <- stashCommentVotes += vote
        _ <- {
          val votePointName = if (vote.isVoteUp) "total_up_vote_points" else "total_down_vote_points"
          val voteCountName = if (vote.isVoteUp) "up_vote_count" else "down_vote_count"
          val votePointAdd = if (vote.isVoteUp) vote.votePoint else -vote.votePoint
          val votePointSet = s"s.$votePointName = s.$votePointName + ${vote.votePoint}"
          val allVotePointSet = s"s.total_all_vote_points = s.total_all_vote_points + ${votePointAdd}"
          val voteCountSet = s"s.$voteCountName = s.$voteCountName + 1"
          val allVoteCountSet = s"s.all_vote_count = s.all_vote_count + 1"
          val q = s"update StashComments s set ${votePointSet}, ${allVotePointSet}, ${voteCountSet}, ${allVoteCountSet} where s.id = '${vote.stashCommentId}'"
          sqlu"#$q"
        }
      } yield ()).transactionally
      db.run(a)
    }

    def deleteCommentVote(vote: models.StashCommentVote): Future[Unit] = {
      val a = (for {
        v <- stashCommentVotes.filter(f => f.stashCommentId === vote.stashCommentId && f.ownerId === vote.ownerId).map(_.votePoint).result.headOption
        _ <- {
          val votePoint = v.get
          val votePointName = if (vote.isVoteUp) "total_up_vote_points" else "total_down_vote_points"
          val voteCountName = if (vote.isVoteUp) "up_vote_count" else "down_vote_count"
          val votePointAdd = if (vote.isVoteUp) votePoint else -votePoint
          val votePointSet = s"s.$votePointName = s.$votePointName - ${votePoint}"
          val allVotePointSet = s"s.total_all_vote_points = s.total_all_vote_points - ${votePointAdd}"
          val voteCountSet = s"s.$voteCountName = s.$voteCountName - 1"
          val allVoteCountSet = s"s.all_vote_count = s.all_vote_count - 1"
          val q = s"update StashComments s set ${votePointSet}, ${allVotePointSet}, ${voteCountSet}, ${allVoteCountSet} where s.id = '${vote.stashCommentId}'"
          sqlu"#$q"
        }
        _ <- stashCommentVotes.filter(f => f.stashCommentId === vote.stashCommentId && f.ownerId === vote.ownerId).delete
      } yield ()).transactionally
      db.run(a)
    }

    def updateCommentVote(vote: models.StashCommentVote, isVoteUp: Boolean): Future[Unit] = {
      if (vote.isVoteUp != isVoteUp) {
        val a = (for {
          v <- stashCommentVotes.filter(f => f.stashCommentId === vote.stashCommentId && f.ownerId === vote.ownerId).map(_.votePoint).result.headOption
          _ <- {
            val votePoint = v.get
            val votePointNamePlus = if (isVoteUp) "total_up_vote_points" else "total_down_vote_points"
            val votePointNameMinus = if (isVoteUp) "total_down_vote_points" else "total_up_vote_points"
            val voteCountNamePlus = if (isVoteUp) "up_vote_count" else "down_vote_count"
            val voteCountNameMinus = if (isVoteUp) "down_vote_count" else "up_vote_count"
            val votePointPlusSet = s"s.$votePointNamePlus = s.$votePointNamePlus + ${vote.votePoint}"
            val votePointMinusSet = s"s.$votePointNameMinus = s.$votePointNameMinus - ${votePoint}"
            val votePointAllSet = if (isVoteUp) {
              s"s.total_all_vote_points = s.total_all_vote_points + ${vote.votePoint} + ${votePoint}"
            } else {
              s"s.total_all_vote_points = s.total_all_vote_points - ${vote.votePoint} - ${votePoint}"
            }
            val voteCountPlusSet = s"s.$voteCountNamePlus = s.$voteCountNamePlus + 1"
            val voteCountMinusSet = s"s.$voteCountNameMinus = $voteCountNameMinus - 1"
            val q = "update StashComments s set " +
              s"${votePointPlusSet}, ${votePointMinusSet}, ${votePointAllSet}, ${voteCountPlusSet}, ${voteCountMinusSet} " +
              s"where s.id = '${vote.stashCommentId}'"
            sqlu"#$q"
          }
          _ <- stashCommentVotes.filter(f => f.stashCommentId === vote.stashCommentId && f.ownerId === vote.ownerId).map(u => (u.isVoteUp, u.votePoint)).update((isVoteUp, vote.votePoint))
        } yield()).transactionally
        db.run(a)
      } else {
        Future(())
      }
    }
  }

  val userDAO = new UserDAO()
  val projectDAO = new ProjectDAO()
  val topicDAO = new TopicDAO()
  val stashDAO = new StashDAO()
}
