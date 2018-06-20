package model

import java.util.Date

case class DrugExcelRecord (
  regNum: String,
  regDate: Option[Long],
  regExpiredDate: Option[Long],
  regInvalidDate: Option[Long],
  regOwner: String,
  regCountry: String,
  tradeName: String,
  mnn: String,
  forms: String,
  prodStage: String,
  barCodes: String,
  normDocs: String,
  group: String
)

case class DrugInfo(
  tn: String, 
  mnn: String, 
  lekForm: String, 
  regOwner: String, 
  regCountry: String, 
  regNumber: String,
  regDate: Date, 
  regExpiredDate: Date, 
  regInvalidDate: Date, 
  regChangedDate: Date,
  regAcceptRequest: String, 
  regAcceptResponse: String,
  // Vital and essential drugs
  VED: String, 
  comments: String, 
  drug: Boolean,
  forms: List[Form],
  instructions: List[Instruction]
)

case class Form (
  position: Int,
  formId: String,
  name: String,
  dosage: String,
  userPackage: String,
  amountPerUserPack: String,
  primaryPackage: String,
  amountPerPrimaryPack: String,
  amountPrimaryPack: String,
  complect: String,
  shelfLife: String
)

case class Instruction (
  name: String,
  data: Array[Byte],
  href: String,
  contentType: String
)