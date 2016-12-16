package ru.shoppinglive.chat.chat_api

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import akka.persistence.PersistentActor
import ru.shoppinglive.chat.chat_api.Cmd._
import ru.shoppinglive.chat.chat_api.ConversationSupervisor._
import ru.shoppinglive.chat.chat_api.Event.{DialogCreated, MsgConsumed, MsgPosted}
import ru.shoppinglive.chat.chat_api.Result._
import ru.shoppinglive.chat.domain.Dialog


/**
  * Created by rkhabibullin on 13.12.2016.
  */

object Conversation {

  def props(id:Int) = Props(new Conversation(id))
}

class Conversation(id:Int) extends PersistentActor with ActorLogging{
  val api = new Dialog(id)
  var lastEvent = 0

  override def receiveRecover: Receive = LoggingReceive {
    case MsgPosted(_,_,time,fromUser,text) =>
      val msg = Dialog.Msg(text, time, fromUser)
      api.newMsg(msg)
    case MsgConsumed(_,_,_,fromUser) => api.consume(fromUser)
  }

  override def receiveCommand: Receive = LoggingReceive {
    case AuthenticatedCmd(fromUser, cmd, replyTo) => cmd match {
      case ReadCmd(_, from, to) => if(api.hasNew(fromUser)){
        lastEvent+=1
        persist(MsgConsumed(lastEvent, id, System.currentTimeMillis(), fromUser)){context.parent ! _}
      }
      replyTo ! DialogMsgList(id, api.read(fromUser, from, to), api.total, from, to)
      case MsgCmd(_, text) =>
        lastEvent+=1
        persist(MsgPosted(lastEvent, id, System.currentTimeMillis(), fromUser, text)){
          e => val msg = Dialog.Msg(e.msg, e.time, e.from)
          api.newMsg(msg)
          context.parent ! e
        }
      case _ =>
    }
  }

  override def persistenceId:String = "dlg-"+id

}
