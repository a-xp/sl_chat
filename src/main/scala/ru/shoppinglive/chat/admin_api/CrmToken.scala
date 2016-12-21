package ru.shoppinglive.chat.admin_api

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.agent.Agent
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import org.json4s.CustomSerializer
import org.json4s.JsonAST.{JInt, JString}
import ru.shoppinglive.chat.admin_api.CrmToken.{AuthFailed, AuthSuccess, StringToInt, TokenInfo}
import ru.shoppinglive.chat.chat_api.Cmd.TokenCmd
import ru.shoppinglive.chat.domain.Crm
import scaldi.{Injectable, Injector}

import scala.util.Success

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class CrmToken(implicit inj:Injector) extends Actor with ActorLogging with Injectable{

  implicit private val system = inject [ActorSystem]
  implicit private val materializer = ActorMaterializer()
  implicit private val ec = system.dispatcher
  private val usersDb = inject [Agent[Seq[Crm.User]]] ('usersDb)

  private val http = Http()
  val crmApiUrl = "http://rkhabibullin.old.shoppinglive.ru/crm/modules/ajax/authorization/token/info?token="

  override def receive: Receive = {
    case TokenCmd(token) =>
      val originalSender = sender
      http.singleRequest(HttpRequest(uri = crmApiUrl + token)) flatMap {
        case HttpResponse(StatusCodes.OK, _, entity,_) =>
          Unmarshal(entity).to[String]
      } map {
        import org.json4s.DefaultFormats
        import org.json4s.native.Serialization.read
        implicit val formats = DefaultFormats + StringToInt
        read[TokenInfo]
      } onComplete{
        case Success(ti:TokenInfo) => println(ti)
          usersDb().find(_.crmId==ti.id).map(AuthSuccess) match {
            case Some(a) => originalSender ! a
            case None => originalSender ! AuthFailed
          }
        case _ =>
          originalSender ! AuthFailed
      }
  }
}

object CrmToken {
  def props(implicit inj:Injector) = Props(new CrmToken)

  case class TokenInfo(id:Int, valid:Long)
  case class AuthSuccess(user:Crm.User)
  case object AuthFailed

  object StringToInt extends CustomSerializer[Long](format => ({ case JString(x) => x.toInt }, { case x: Int => JInt(x) }))
}
