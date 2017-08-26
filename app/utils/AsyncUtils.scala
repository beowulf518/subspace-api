package utils

import scala.concurrent.Await
import scala.concurrent.Awaitable
import scala.concurrent.duration.DurationInt

object AsyncUtils {
  val asyncTimeout = 10 seconds
  def await[R](action:  Awaitable[R]): R = Await.result(action, AsyncUtils.asyncTimeout)
}
