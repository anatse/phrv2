package modules

import actors.LoadRls
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport

class ActorsModule extends ScalaModule with AkkaGuiceSupport {
  override def configure () = {
    bindActor[LoadRls]("load-rls-actor")
  }
}