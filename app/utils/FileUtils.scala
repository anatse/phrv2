package utils

object FileUtils {
  private val ext = """\.[A-Za-z0-9]+$""".r
  def extractExt(url: String) = ext findFirstIn url getOrElse("")
}
