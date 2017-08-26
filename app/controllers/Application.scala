package controllers

import javax.inject.Inject
import akka.actor.ActorSystem
import play.api.libs.json._
import play.api.mvc._
import play.api.Configuration
import sangria.execution._
import sangria.parser.{QueryParser, SyntaxError}
import sangria.marshalling.playJson._
import dao.models.{GraphQLContext, SchemaDefinition}
import sangria.renderer.SchemaRenderer
import dao.{DAOContainer}
import dao.RepositoryDAO
import dao.models.User
import scala.concurrent.Future
import scala.util.{Failure, Success}
import utils.ViewerAction
import utils.{GitblitUtil, JGitUtil}

class Application @Inject() (system: ActorSystem, config: Configuration, userAction: ViewerAction, repoDAO: RepositoryDAO, jGitUtil: JGitUtil, gitBlitUtil: GitblitUtil, allDao: DAOContainer
) extends Controller {
  import system.dispatcher

  val defaultGraphQLUrl = config.getString("defaultGraphQLUrl").getOrElse(s"http://localhost:${config.getInt("http.port").getOrElse(9000)}/graphql")

  def graphiql = Action {
    Ok(views.html.graphiql())
  }

  def graphiqlOptions = Action {
    Ok("").withHeaders(
      "Access-Control-Allow-Origin" -> "*", // TODO: read from config
      "Access-Control-Allow-Methods" -> "GET, POST",
      "Access-Control-Allow-Headers" -> "Accept, Origin, Content-type, X-Requested-With, fbase, stexch",
      "Access-Control-Allow-Credentials" -> "true",
      "Access-Control-Max-Age" -> (60 * 60 * 24).toString
    )
  }

  def graphql(query: String, variables: Option[String], operation: Option[String]) =
    userAction.async { request => executeQuery(query, variables map parseVariables, operation, request.viewer) }

  def graphqlBody = userAction.async(parse.json) { request ⇒
    val query = (request.body \ "query").as[String]
    val operation = (request.body \ "operationName").asOpt[String]

    val variables = (request.body \ "variables").toOption.flatMap {
      case JsString(vars) ⇒ Some(parseVariables(vars))
      case obj: JsObject ⇒ Some(obj)
      case _ ⇒ None
    }

    executeQuery(query, variables, operation, request.viewer)
  }

  private def parseVariables(variables: String) =
    if (variables.trim == "" || variables.trim == "null") Json.obj() else Json.parse(variables).as[JsObject]

  private def executeQuery(query: String, variables: Option[JsObject], operation: Option[String], user: Option[User]) =
    QueryParser.parse(query) match {

      // query parsed successfully, time to execute it!
      case Success(queryAst) ⇒
        Executor.execute(
            SchemaDefinition.schema,
            queryAst,
            GraphQLContext(repoDAO, jGitUtil, gitBlitUtil, allDao, user),
            operationName = operation,
            variables = variables getOrElse Json.obj(),
            exceptionHandler = exceptionHandler,
            queryReducers = List(
              QueryReducer.rejectMaxDepth[GraphQLContext](15),
              QueryReducer.rejectComplexQueries[GraphQLContext](4000, (_, _) ⇒ TooComplexQueryError)))
          .map(Ok(_))
          .recover {
            case error: QueryAnalysisError ⇒ BadRequest(error.resolveError)
            case error: ErrorWithResolver ⇒ InternalServerError(error.resolveError)
          }

      // can't parse GraphQL query, return error
      case Failure(error: SyntaxError) ⇒
        Future.successful(BadRequest(Json.obj(
          "syntaxError" → error.getMessage,
          "locations" → Json.arr(Json.obj(
            "line" → error.originalError.position.line,
            "column" → error.originalError.position.column)))))

      case Failure(error) ⇒
        throw error
    }

  def renderSchema = Action {
    Ok(SchemaRenderer.renderSchema(SchemaDefinition.schema))
  }

  lazy val exceptionHandler: Executor.ExceptionHandler = {
    case (_, error @ TooComplexQueryError) ⇒ HandledException(error.getMessage)
    case (_, error @ MaxQueryDepthReachedError(_)) ⇒ HandledException(error.getMessage)
  }

  case object TooComplexQueryError extends Exception("Query is too expensive.")
}
