package ru.shoppinglive.chat.chat_api

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.stream.ActorMaterializer
import ru.shoppinglive.chat.chat_api.ConversationSupervisor.DialogInfo
import scaldi.{Injectable, Injector}

import scala.collection.mutable

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class ConversationSupervisor(implicit inj:Injector)  extends Actor with ActorLogging with Injectable{
  import ru.shoppinglive.chat.chat_api.Cmd._
  implicit val materializer = ActorMaterializer()
  private val dlgUsers = mutable.Map.empty[Int, Set[Int]]
  private val dlgActors = mutable.Map.empty[Int, ActorRef]

  override def receive: Receive = LoggingReceive {
    case athcmd @ AuthenticatedCmd(from, cmd, replyTo) => cmd match {
      case ReadCmd(dlgId,_,_) => sendCmd(dlgId, cmd)
      case MsgCmd(dlgId,_) => sendCmd(dlgId, cmd)
    }
    case DialogInfo(id, users) => dlgUsers(id) = users
  }

  private def sendCmd(dlgId:Int, cmd:Any) = {
    dlgActors.getOrElseUpdate(dlgId, context.system.actorOf(Conversation.props(dlgId, dlgUsers(dlgId)))) ! cmd
  }
}

object ConversationSupervisor{
  case class DialogInfo(id:Int, users:Set[Int])

  def props = Props(new ConversationSupervisor)
}