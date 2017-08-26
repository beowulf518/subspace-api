package dao

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import dao.models.{Repository, User, DatabaseTables}
import utils.{AsyncUtils, MaybeFilter}
import scala.concurrent.Future

// TODO: extend from BaseDAO
@Singleton
class RepositoryDAO @Inject() (system: ActorSystem, dbTables: DatabaseTables) {

  import dbTables._
  import dbTables.dbConfig.db
  import dbTables.dbConfig.driver.api._
  import slick.lifted.Rep

  import system.dispatcher

  def create(newRepo: Repository): Future[Int] = {
    db.run(repos += newRepo)
  }

  def find(id: String): Option[Repository] = AsyncUtils.await(db.run(repos.filter(_.id === id).result.headOption))

  def all: Seq[Repository] = AsyncUtils.await(db.run(repos result))
  
  def getBy(id: Option[String], name: Option[String], ownerId: Option[String], ownerName: Option[String]): Future[Seq[Repository]] = {
    val filterQuery = MaybeFilter(repos)
      .filter(id)(v => d => d.id === v)
      .filter(name)(v => d => d.name === v)
      .filter(ownerId)(v => d => d.ownerId === v)
      .filter(ownerName)(v => d => {
        val user = AsyncUtils.await(db.run(users.filter(_.userName === ownerName).result.headOption)).get
        d.ownerId === user.id
      })
      .query
    
    println(filterQuery);
    
    val joinQuery = filterQuery join users on (_.ownerId === _.id)
    db.run((for {
      (c, s) <- joinQuery
    } yield (c)).result)
  }
}