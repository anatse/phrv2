package modules

import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.actions.{SecuredErrorHandler, UnsecuredErrorHandler}
import com.mohiva.play.silhouette.api.crypto.{Base64AuthenticatorEncoder, Crypter}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.crypto.{JcaCrypter, JcaCrypterSettings}
import com.mohiva.play.silhouette.impl.authenticators.{JWTAuthenticator, JWTAuthenticatorService, JWTAuthenticatorSettings}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.impl.util.{DefaultFingerprintGenerator, PlayCacheLayer, SecureRandomIDGenerator}
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import model.security.JWTEnv
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import service.impl.{PhrAuthInfoService}
import service.security.{PhrIdentityService, PhrSecuredErrorHandlerImpl, PhrUnsecuredErrorHandlerImpl}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration.{Duration, FiniteDuration}

class SecurityModule extends AbstractModule with ScalaModule {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def configure() = {
    bind[Silhouette[JWTEnv]].to[SilhouetteProvider[JWTEnv]]   // Adding JWN environment
    bind[CacheLayer].to[PlayCacheLayer] // Adding cache
//    bind[DelegableAuthInfoDAO[PasswordInfo]].to[PhrAuthInfoServiceImpl] // Add password storage

    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher)
    bind[FingerprintGenerator].toInstance(new DefaultFingerprintGenerator(false))
    bind[EventBus].toInstance(EventBus())
    bind[Clock].toInstance(Clock())

    // Error handlers
    bind[SecuredErrorHandler].to[PhrSecuredErrorHandlerImpl]
    bind[UnsecuredErrorHandler].to[PhrUnsecuredErrorHandlerImpl]
  }

  @Provides
  def provideJWTEnvironment(
    identityService: PhrIdentityService,
    authenticatorService: AuthenticatorService[JWTAuthenticator],
    eventBus: EventBus): Environment[JWTEnv] = {

    Environment[JWTEnv](
      identityService,
      authenticatorService,
      Seq(),
      eventBus)
  }

  @Provides @Named("authenticator-crypter")
  def provideAuthenticatorCrypter(configuration: Configuration): Crypter = {
    val config = configuration.underlying.as[JcaCrypterSettings]("silhouette.authenticator.crypter")
    new JcaCrypter(config)
  }

  @Provides
  def provideJWTAuthenticatorService(
    @Named("authenticator-crypter") crypter: Crypter,
    idGenerator: IDGenerator,
    configuration: Configuration,
    clock: Clock): AuthenticatorService[JWTAuthenticator] = {

    val authenticatorService: AuthenticatorService[JWTAuthenticator] = {
      val authenticatorDecoder = new Base64AuthenticatorEncoder
      val duration = configuration.underlying.getString("silhouette.jwt.authenticator.authenticatorExpiry")
      val expiration = Duration.apply(duration).asInstanceOf[FiniteDuration]
      val config = new JWTAuthenticatorSettings(
                          fieldName = configuration.underlying.getString("silhouette.jwt.authenticator.headerName"),
                          issuerClaim = configuration.underlying.getString("silhouette.jwt.authenticator.issuerClaim"),
                          authenticatorExpiry = expiration,
                          sharedSecret = configuration.underlying.getString("silhouette.jwt.authenticator.sharedSecret")
                   )

      new JWTAuthenticatorService(config, None, authenticatorDecoder, idGenerator, clock)
    }

    authenticatorService
  }

  /**
    * Provides the password hasher registry.
    *
    * @param passwordHasher The default password hasher implementation.
    * @return The password hasher registry.
    */
  @Provides
  def providePasswordHasherRegistry(passwordHasher: PasswordHasher): PasswordHasherRegistry = {
    PasswordHasherRegistry(passwordHasher)
  }

  /**
    * Provides the credentials provider.
    *
    * @param authInfoRepository The auth info repository implementation.
    * @param passwordHasherRegistry The password hasher registry.
    * @return The credentials provider.
    */
  @Provides
  def provideCredentialsProvider(authInfoRepository: AuthInfoRepository, passwordHasherRegistry: PasswordHasherRegistry): CredentialsProvider = {
    new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
  }

  /**
    * Provides the auth info repository.
    *
    * @param passwordInfoDAO The implementation of the delegable password auth info DAO.
    * @return The auth info repository instance.
    */
  @Provides
  def provideAuthInfoRepository(passwordInfoDAO: PhrAuthInfoService): AuthInfoRepository = {
    new DelegableAuthInfoRepository(passwordInfoDAO)
  }
}
