package actors

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.poi.ss.usermodel.{Cell, CellType, Row}

object RlsUtils {
  sealed class Field (val index: Int)
  case object REG_NUM extends Field(0)
  case object REG_DATE extends Field( 1)
  case object REG_EXPIRED_DATE extends Field(2)
  case object REG_INVALID_DATE extends Field(3)
  case object REG_OWNER extends Field(5)
  case object REG_OWNER_COUNTRY extends Field(6)
  case object TRADE_NAME extends Field(7)
  case object MNN extends Field(9)
  case object FORM extends Field(10)
  case object PROD_STAGES extends Field(11)
  case object BAR_CODES extends Field(12)
  case object NORM_DOC extends Field(13)
  case object GROUP extends Field(14)

  implicit class DateUtils (date: Date) {
    def getOptionTime (): Option[Long] = {
      if (date == null) None else Some(date.getTime)
    }
  }

  implicit class RowlUtils (row: Row) {
    val format = new SimpleDateFormat("dd.MM.yyyy")

    def getString (cellNum: Field): Option[String] = {
      row.getCell(cellNum.index) match {
        case cel:Cell => cel.getCellTypeEnum match {
          case CellType.STRING => Some(cel.getStringCellValue)
          case CellType.BOOLEAN => Some(cel.getBooleanCellValue.toString)
          case CellType.NUMERIC => Some(cel.getNumericCellValue.intValue().toString)
          case _ => None
        }
        case _ => None
      }
    }

    def getInt (cellNum: Field): Option[Int] = {
      row.getCell(cellNum.index) match {
        case cel:Cell => cel.getCellTypeEnum match {
          case CellType.STRING => Some(cel.getStringCellValue.toInt)
          case CellType.NUMERIC => Some(cel.getNumericCellValue.intValue())
          case _ => None
        }
        case _ => None
      }
    }

    def getDate (cellNum: Field): Date = {
      row.getCell(cellNum.index) match {
        case cel:Cell => cel.getCellTypeEnum match {
          case CellType.STRING => format.parse(cel.getStringCellValue)
          case CellType.NUMERIC => cel.getDateCellValue
          case _ => null
        }
        case _ => null
      }
    }
  }
}
