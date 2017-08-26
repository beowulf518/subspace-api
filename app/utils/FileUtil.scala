package utils

object FileUtil {

  def isLarge(size: Long): Boolean = (size > 1024 * 1000)
}
