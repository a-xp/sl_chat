package ru.shoppinglive.chat.client_connection

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.event.LoggingReceive
import akka.http.scaladsl.model.ws.Message
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import ru.shoppinglive.chat.admin_api.CrmToken.{AuthFailed, AuthSuccess}
import ru.shoppinglive.chat.chat_api.ConversationSupervisor._
import ru.shoppinglive.chat.client_connection.ConnectionSupervisor.CreateConnection

import scala.util.{Failure, Success}

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class Connection extends Actor with ActorLogging {

  var clientId = 0
  var out:Option[ActorRef] = None
  var in:Option[ActorRef] = None
  implicit val materializer = ActorMaterializer()
  implicit val ec = context.dispatcher
  val core = context.actorSelection("/user/chat")
  val auth = context.actorSelection("/user/auth")

  import Connection._
  override def receive:Receive = creatingStreams

  def creatingStreams:Receive = LoggingReceive {
    case CreateConnection =>
      sender ! (Sink.actorSubscriber[Message](Reciever.props(self)),
        Source.actorPublisher[Message](Sender.props(self)))
    case SenderRdy => out = Some(sender)
      context.watch(sender)
      if(in.isDefined)context.become(authenticating)
    case RecieverRdy => in = Some(sender)
      context.watch(sender)
      if(out.isDefined)context.become(authenticating)
  }

  def authenticating:Receive = LoggingReceive {
    case cmd @ TokenCmd(_) => auth ! cmd
    case AuthSuccess(user) => clientId = user.id
      core ! AuthenticatedCmd(user.id, ConnectedCmd(), self)
      out.get ! AuthSuccessResult(user.role.code, user.role.name, user.login)
      context.become(listening)
    case AuthFailed => out.get ! AuthFailedResult("can not authorize")
  }

  def listening:Receive = LoggingReceive {
    case result: Result => out.get ! result
    case cmd: Cmd => core ! AuthenticatedCmd(clientId, cmd, self)
  }
}

object Connection {
  def props = Props[Connection]

  case object SenderRdy
  case object RecieverRdy
  case class ClientAuthenticated(id:Int)
}


