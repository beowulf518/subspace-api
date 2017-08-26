import com.google.inject.Inject
import filters.LoggingFilter
import play.api.http.HttpFilters

class Filters @Inject()(log: LoggingFilter) extends HttpFilters {
  val filters = Seq(log)
}