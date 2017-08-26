package utils

import java.util.UUID

object RandomGenerator {
  def randomUUIDString: String = UUID.randomUUID().toString
}
