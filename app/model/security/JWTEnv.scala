package model.security

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator

trait JWTEnv extends Env {
  type I = PhrUser
  type A = JWTAuthenticator
}
