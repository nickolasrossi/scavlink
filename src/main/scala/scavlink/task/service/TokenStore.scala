package scavlink.task.service

import java.security.SecureRandom

import org.parboiled.common.Base64
import spray.caching.LruCache
import spray.routing.authentication.{BasicUserContext, UserPass}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

case class Token(token: String) {
  override def toString = token
}

object Token {
  private val random = new SecureRandom
  private val base64 = Base64.custom()

  def generate(): Token = {
    val bytes = new Array[Byte](24)
    random.nextBytes(bytes)
    new Token(base64.encodeToString(bytes, false))
  }

  def dummy(): Token = new Token("01234567890123456789012345678901")
}


/**
 * Interface to a token store that plays nice with spray's authentication directives.
 * @author Nick Rossi
 */
trait TokenStore {
  def checkUserPass(up: Option[UserPass])(implicit ec: ExecutionContext) = check(up.map(u => new Token(u.pass)))
  def checkString(token: Option[String])(implicit ec: ExecutionContext) = check(token.map(Token.apply))
  def check(token: Option[Token])(implicit ec: ExecutionContext): Future[Option[BasicUserContext]]

  def addUserPass(up: UserPass)(implicit ec: ExecutionContext): Token = addUser(BasicUserContext(up.user))
  def addUser(user: BasicUserContext)(implicit ec: ExecutionContext): Token
}

/**
 * Basic in-memory token store using spray LruCache.
 * Tokens are reaped if unused after the configured duration.
 * @author Nick Rossi
 */
class MemoryTokenStore(idleTimeout: FiniteDuration) extends TokenStore {
  private val tokens = LruCache[BasicUserContext](timeToIdle = idleTimeout)

  def check(token: Option[Token])(implicit ec: ExecutionContext): Future[Option[BasicUserContext]] =
    tokens.get(token.getOrElse(None)) match {
      case Some(future) => future.map(Option.apply)
      case None => Future.successful(None)
    }

  def addUser(user: BasicUserContext)(implicit ec: ExecutionContext): Token = {
    val token = Token.dummy()
    tokens(token) { user }
    token
  }
}
