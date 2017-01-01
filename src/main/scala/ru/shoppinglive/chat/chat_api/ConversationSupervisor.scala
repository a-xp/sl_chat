package ru.shoppinglive.chat.chat_api

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.agent.Agent
import akka.event.LoggingReceive
import akka.stream.ActorMaterializer
import ru.shoppinglive.chat.chat_api.ConversationSupervisor.{DialogInfo, ResetDialog}
import ru.shoppinglive.chat.domain.DialogHeader
import scaldi.{Injectable, Injector}

import scala.collection.mutable

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class ConversationSupervisor(implicit inj:Injector)  extends Actor with ActorLogging with Injectable{
  import ru.shoppinglive.chat.chat_api.Cmd._
  implicit val materializer = ActorMaterializer()
  private val dlgDb = inject [Agent[Map[Int, DialogHeader]]] ('dialogsDb)
  private val dlgActors = mutable.Map.empty[Int, ActorRef]


  override def receive: Receive = LoggingReceive {
    case athcmd @ AuthenticatedCmd(from, cmd, replyTo) => cmd match {
      case ReadCmd(dlgId,_,_) => sendCmd(dlgId, athcmd)
      case MsgCmd(dlgId,_) => sendCmd(dlgId, athcmd)
      case ReadNewCmd(dlgId) => sendCmd(dlgId, athcmd)
      case _ =>
    }
    case "reset" => dlgDb().values foreach(dlg=>sendCmd(dlg.id, "reset"))
  }

  private def sendCmd(dlgId:Int, cmd:Any) = {
    dlgActors.getOrElseUpdate(dlgId, context.actorOf(Conversation.props(dlgId), "dlg-"+dlgId)) ! cmd
  }
}

object ConversationSupervisor{
  case class DialogInfo(id:Int, users:Set[Int])
  case class ResetDialog(id:Int)

  def props(implicit inj:Injector) = Props(new ConversationSupervisor)
}