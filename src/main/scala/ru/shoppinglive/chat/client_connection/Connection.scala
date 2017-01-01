package ru.shoppinglive.chat.client_connection

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Sink, Source}
import ru.shoppinglive.chat.admin_api.CrmToken.{AuthFailed, AuthSuccess}
import ru.shoppinglive.chat.chat_api.Cmd._
import ru.shoppinglive.chat.chat_api.Result._
import ru.shoppinglive.chat.chat_api.{Cmd, Result}
import ru.shoppinglive.chat.client_connection.ConnectionSupervisor.CreateConnection
import scaldi.{Injectable, Injector}

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class Connection(implicit inj:Injector) extends Actor with ActorLogging with Injectable{

  var clientId = 0
  var out:Option[ActorRef] = None
  var in:Option[ActorRef] = None

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
    case cmd @ TokenCmd(_) => inject [ActorRef] ('auth) ! cmd
    case AuthSuccess(user) => clientId = user.id
      inject [ActorRef] ('notifier) ! AuthenticatedCmd(user.id, ConnectedCmd, self)
      out.get ! AuthSuccessResult(user.role.code, user.role.name, user.login, user.id)
      context.become(listening)
    case AuthFailed => out.get ! AuthFailedResult("can not authorize")
  }

  def listening:Receive = LoggingReceive {
    case result: Result => out.get ! result
    case cmd: Cmd => cmd match {
      case MsgCmd(_,_) | ReadCmd(_,_,_) | ReadNewCmd(_) => inject [ActorRef] ('dialogs) ! AuthenticatedCmd(clientId, cmd, self)
      case _ => inject [ActorRef] ('contacts) ! AuthenticatedCmd(clientId, cmd, self)
    }
  }
}

object Connection {
  def props(implicit inj:Injector) = Props(new Connection)

  case object SenderRdy
  case object RecieverRdy
}


