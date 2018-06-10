package modules

import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}
import service.DrugService
import service.impl.DrugServiceImpl

class ProductDaoModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq (
    bind[DrugService].to[DrugServiceImpl]
  )
}
