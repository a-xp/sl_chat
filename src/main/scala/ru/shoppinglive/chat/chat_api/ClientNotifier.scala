package ru.shoppinglive.chat.chat_api

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import ru.shoppinglive.chat.chat_api.ClientNotifier.AddressedMsg
import ru.shoppinglive.chat.chat_api.Cmd.{AuthenticatedCmd, ConnectedCmd, DisconnectedCmd}
import scaldi.{Injectable, Injector}

import scala.collection.mutable

/**
  * Created by rkhabibullin on 20.12.2016.
  */
class ClientNotifier(implicit inj:Injector) extends Actor with ActorLogging with Injectable{
  private val users = mutable.Map.empty[Int, List[ActorRef]]

  override def receive: Receive = LoggingReceive {
    case AuthenticatedCmd(fromUser, cmd, replyTo) => cmd match {
      case ConnectedCmd =>
        users(fromUser) = replyTo :: users.getOrElse(fromUser, Nil)
      case DisconnectedCmd =>
        users(fromUser) = users(fromUser) filter(_!=replyTo)
    }
    case AddressedMsg(to, msg) =>
      users get to foreach(_ foreach(_ ! msg))
  }
}

object ClientNotifier {
  case class AddressedMsg(to:Int, msg:Any)

  def props = Props(new ClientNotifier)
}