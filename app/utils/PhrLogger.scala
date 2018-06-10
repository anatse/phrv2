package utils

trait PhrLogger { self =>
  val logger = play.api.Logger(self.getClass)
}
