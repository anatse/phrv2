package model

import java.util.UUID

import org.joda.time.DateTime
import reactivemongo.api.Cursor
import reactivemongo.bson.{BSONDateTime, BSONReader, BSONString, BSONWriter}
import utils.PhrLogger

trait MongoBaseDao extends PhrLogger {
  def handler[T]: Cursor.ErrorHandler[List[T]] = { (last: List[T], error: Throwable) =>
    logger.debug(s"Encounter error: $error, result size: ${last.size}")

    if (last.isEmpty)
      Cursor.Cont(last)
    else
      Cursor.Fail(error)
  }

  implicit object DateTimeConverter extends BSONWriter[DateTime, BSONDateTime] with BSONReader[BSONDateTime, DateTime] {
    override def write(dateTime: DateTime): BSONDateTime = BSONDateTime(dateTime.getMillis)
    override def read(bson: BSONDateTime): DateTime = new DateTime(bson.value)
  }

  implicit object UUIDConverter extends BSONWriter[UUID, BSONString] with BSONReader[BSONString, UUID] {
    override def write(uuid: UUID): BSONString = BSONString(uuid.toString)
    override def read(bson: BSONString): UUID = UUID.fromString(bson.value)
  }
}
