package ru.shoppinglive.chat.chat_api

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.agent.Agent
import akka.event.LoggingReceive
import akka.persistence.PersistentActor
import ru.shoppinglive.chat.chat_api.Cmd._
import ru.shoppinglive.chat.chat_api.ConversationSupervisor._
import ru.shoppinglive.chat.chat_api.Event.{DialogCreated, MsgConsumed, MsgPosted}
import ru.shoppinglive.chat.chat_api.Result._
import ru.shoppinglive.chat.domain.{Dialog, DialogHeader}
import scaldi.{Injectable, Injector}


/**
  * Created by rkhabibullin on 13.12.2016.
  */

object Conversation {

  def props(id:Int)(implicit inj:Injector) = Props(new Conversation(id))
}

class Conversation(id:Int)(implicit inj:Injector) extends PersistentActor with ActorLogging with Injectable{
  private val dlgDb = inject [Agent[Map[Int, DialogHeader]]] ('dialogsDb)
  private val users = dlgDb()(id).between
  val api = new Dialog(id, users)

  override def receiveRecover: Receive = LoggingReceive {
    case MsgPosted(_,time,fromUser,text) => api.newMsg(Dialog.Msg(text, time, fromUser))
    case MsgConsumed(_,_,fromUser) => api.consume(fromUser)
  }

  override def receiveCommand: Receive = LoggingReceive {
    case AuthenticatedCmd(fromUser, cmd, replyTo) => cmd match {
      case ReadCmd(_, from, to) => if(api.hasNew(fromUser)){
        val msg = MsgConsumed(id, System.currentTimeMillis(), fromUser)
        persistAsync(msg){e => }
        inject [ActorRef] ('contacts) ! msg
      }
      replyTo ! DialogMsgList(id, api.read(fromUser, from, to), api.total, from, to)
      case MsgCmd(_, text) =>
        val msg = MsgPosted(id, System.currentTimeMillis(), fromUser, text)
        persistAsync(msg){ e =>  }
        api.newMsg(Dialog.Msg(msg.msg, msg.time, msg.from))
        inject [ActorRef] ('contacts) ! msg
        replyTo ! DialogMsgAccepted(id, text.##, msg.time)
      case ReadNewCmd(_) =>
        if(api.hasNew(fromUser)){
          val msg = MsgConsumed(id, System.currentTimeMillis(), fromUser)
          persistAsync(msg){e => }
          inject [ActorRef] ('contacts) ! msg
          replyTo ! DialogNewMsg(id, api.readNew(fromUser))
        }
      case _ =>
    }
    case "reset" => deleteMessages(Long.MaxValue)
      self ! PoisonPill
  }

  override def persistenceId:String = "dlg-"+id
}
