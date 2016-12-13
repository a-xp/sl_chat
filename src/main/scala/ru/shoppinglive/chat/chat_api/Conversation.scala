package ru.shoppinglive.chat.chat_api

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import ru.shoppinglive.chat.chat_api.Supervisor._
import ru.shoppinglive.chat.domain.Dialog


/**
  * Created by rkhabibullin on 13.12.2016.
  */

object Conversation {

  def props(id:Int) = Props(new Conversation(id))
}

class Conversation(id:Int) extends Actor with ActorLogging{
  val api = new Dialog(id)

  override def receive: Receive = LoggingReceive {
    case AuthenticatedCmd(fromUser, cmd, replyTo) => cmd match {
      case ReadCmd(_, from, to) => replyTo ! DialogMsgList(id, api.read(from, to), api.total, from, to)
      case MsgCmd(_, msg, time) =>
        val msg = Dialog.Msg(msg, time, fromUser)
        api.newMsg(msg)
        replyTo ! DialogNewMsg(id, List(msg))
    }
  }
}
