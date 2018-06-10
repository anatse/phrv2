package utils

import org.scalatestplus.play.PlaySpec

class PhrLoggerSpec extends PlaySpec {
  "Phr logger service" should {
    "take implemented class into logger" in {
      class MyTest extends PhrLogger {}
      val tst = new MyTest()
      tst.logger.logger.getName.contains("MyTest") mustBe(true)
    }
  }
}
