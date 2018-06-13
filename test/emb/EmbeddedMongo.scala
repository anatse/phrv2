package emb

import de.flapdoodle.embed.mongo.{Command, MongodStarter}
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net, RuntimeConfigBuilder}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.config.IRuntimeConfig
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network
import org.slf4j.LoggerFactory
import reactivemongo.api.{DefaultDB, MongoDriver}

import scala.concurrent.Future

/**
  * Class for emulate mongodb locally
  * @param port
  */
class EmbeddedMongo(port: Int = 9999) {
  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val logger = LoggerFactory.getLogger(classOf[EmbeddedMongo])
  val nodes = List(s"localhost:$port")

  lazy val driver = new MongoDriver()
  private lazy val connection = driver.connection(nodes)
  private lazy val db: Future[DefaultDB] = connection.database("shopdb")

  lazy val mongodConfig = new MongodConfigBuilder()
    .version(Version.Main.PRODUCTION)
    .net(new Net(port, Network.localhostIsIPv6()))
    .build

  lazy val runtimeConfig: IRuntimeConfig = new RuntimeConfigBuilder()
    .defaultsWithLogger(Command.MongoD, logger)
    .processOutput(ProcessOutput.getDefaultInstanceSilent)
    .build;

  lazy val runtime = MongodStarter.getInstance(runtimeConfig)
  lazy val mongodExecutable = runtime.prepare(mongodConfig)

  def start = mongodExecutable.start
  def stop = mongodExecutable.stop
}
