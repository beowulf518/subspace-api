package filters

import com.google.inject.Inject
import akka.stream.Materializer
import play.api.Logger
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}

class LoggingFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {
  def apply(nextFilter: RequestHeader => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {

    val startTime = System.currentTimeMillis
    Logger.info(s"Request: ${requestHeader.method} on ${requestHeader.uri}")

    nextFilter(requestHeader).map { result =>

      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime

      // Logger.info(s"${requestHeader.method} ${requestHeader.uri}" +
      //  s"took ${requestTime}ms and returned ${result.header.status}")

      Logger.info(s"Response: ${result.header.status} ${result.header.reasonPhrase}, took ${requestTime}ms")

      result.withHeaders(
        "Request-Time" -> requestTime.toString,
        "Access-Control-Allow-Origin" -> "*", // TODO: read from config
        "Access-Control-Allow-Methods" -> "GET, POST",
        "Access-Control-Allow-Headers" -> "Accept, Origin, Content-type, X-Requested-With, fbase, stexch",
        "Access-Control-Allow-Credentials" -> "true",
        "Access-Control-Max-Age" -> (60 * 60 * 24).toString
      )
    }
  }
}
