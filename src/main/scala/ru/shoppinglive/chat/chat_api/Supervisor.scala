package ru.shoppinglive.chat.chat_api

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.agent.Agent
import akka.event.LoggingReceive
import ru.shoppinglive.chat.domain.{Crm, Dialog, DialogList}

import scala.collection.mutable

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class Supervisor(val usersDb: Agent[Seq[Crm.User]], val groupDb: Agent[Seq[Crm.Group]])  extends Actor with ActorLogging {
  import Supervisor._
  private val clients = mutable.Map.empty[Int, Seq[ActorRef]]
  private val conversations = mutable.Map.empty[Int, ActorRef]
  val api = new DialogList

  override def receive: Receive = LoggingReceive {
    case athcmd @ AuthenticatedCmd(from, cmd, replyTo) => cmd match {
      case ConnectedCmd() =>
        clients(from) = clients.getOrElseUpdate(from, mutable.Seq.empty[ActorRef]) :+ replyTo
        replyTo ! ContactsResult(extendContactsList(from, api.listForUser(from), usersDb()))
        mapGroupsList(usersDb()(from-1), groupDb()) foreach( replyTo ! _)
      case DisconnectedCmd() => clients(from) = clients(from).filter(_ != replyTo)
      case CreateDlgCmd(withWhom) =>
        val dlg = api.create(Set(from, withWhom))
        val ntfCreator = ContactUpdate(extendContact(api.getUserView(dlg.id, from), usersDb()))
        clients(from) foreach(_ ! ntfCreator)
        val ntfOther = ContactUpdate(extendContact(api.getUserView(dlg.id, withWhom), usersDb()))
        clients.get(withWhom).foreach(_ foreach (_ ! ntfOther))
      case ReadCmd(dlgId,_,_) =>
        api.acceptedMsg(dlgId, from)
        val ntfCreator = ContactUpdate(extendContact(api.getUserView(dlgId, from), usersDb()))
        clients(from) foreach(_ ! ntfCreator)
        conversations.getOrElseUpdate(dlgId, context.actorOf(Conversation.props(dlgId))) ! athcmd
      case TypingCmd(dlgId) => api.getOthers(dlgId, from) flatMap clients.get foreach( _ foreach (_ ! TypingNotification(dlgId, from)))
      case cmd @ MsgCmd(dlgId, _, _) =>
        val curTime=System.currentTimeMillis()
        api.newMsg(dlgId, from, curTime)
        conversations.getOrElseUpdate(dlgId, context.actorOf(Conversation.props(dlgId))) ! AuthenticatedCmd(from, cmd.copy(time=curTime), replyTo)
        clients(from) foreach(_ ! ContactUpdate(extendContact(api.getUserView(dlgId, from), usersDb())))
        api.getOthers(dlgId, from) foreach {
          otherId => clients.get(otherId) foreach(_ foreach(_ ! ContactUpdate(extendContact(api.getUserView(dlgId, otherId), usersDb()))))
        }
      case BroadcastCmd(group, msg) =>
        val curTime=System.currentTimeMillis()
        usersDb().filter(_.groups.contains(group)).filter(_.id!=from).foreach{
          to => val dlg = api.findOrCreate(Set(to.id, from))
            api.newMsg(dlg.id, from, curTime)
            conversations.getOrElseUpdate(dlg.id, context.actorOf(Conversation.props(dlg.id))) ! AuthenticatedCmd(from, MsgCmd(dlg.id, msg, curTime), replyTo)
            clients.get(to.id) foreach(_ foreach(_ ! ContactUpdate(extendContact(api.getUserView(dlg.id, to.id), usersDb()))))
        }
    }
  }

}

object Supervisor{
  trait Cmd
  case class TokenCmd(token:String) extends Cmd
  case class BroadcastCmd(group: Int, msg:String) extends Cmd
  case class CreateDlgCmd(withWhom:Int) extends Cmd
  case class ReadCmd(dlgId: Int, from:Int=0, to:Int=5) extends Cmd
  case class TypingCmd(dlgId: Int) extends Cmd
  case class MsgCmd(dlgId:Int, msg:String, time:Long=0) extends Cmd
  case class ConnectedCmd() extends Cmd
  case class DisconnectedCmd() extends Cmd
  case class AuthenticatedCmd(from:Int, cmd:Cmd, replyTo:ActorRef)

  trait Result
  case class AuthSuccess(role: String, roleName: String, login:String) extends Result
  case class AuthFailed(reason:String) extends Result
  case class GroupInfo(id:Int, name:String)
  case class GroupsResult(groups:Seq[GroupInfo]) extends Result
  case class ContactInfo(dlgId:Int, userId:Int, login: String, hasNew: Boolean, last: Long)
  case class ContactsResult(contacts: Seq[ContactInfo]) extends Result
  case class ContactUpdate(contact: ContactInfo) extends Result
  case class DialogNewMsg(dlgId:Int, msg:Seq[Dialog.Msg]) extends Result
  case class DialogMsgList(dlgId:Int, msg:Seq[Dialog.Msg], total:Int, from:Int, to:Int) extends Result
  case class TypingNotification(dlgId:Int, who:Int) extends Result

  def extendContactsList(userId:Int, dialogs: Seq[DialogList.DialogUserView], users: Seq[Crm.User]): Seq[ContactInfo] ={
    val user = users(userId-1)
    (if(user.role==Crm.Admin){
        users
      } else{
        users filter (_.role == Crm.Admin)
      }) map { other => dialogs.find(_.to==other.id).map(dlg =>
        ContactInfo(dlg.id, dlg.to, other.login, dlg.hasNew, dlg.lastMsgTime)).getOrElse(
        ContactInfo(0, other.id, other.login, false, 0)
      )}
  }

  def mapGroupsList(user:Crm.User, groups: Seq[Crm.Group]) : Option[GroupsResult] = {
    if(user.role==Crm.Admin){
      Some(GroupsResult(groups map(grp=> GroupInfo(grp.id, grp.name))))
    }else{
      None
    }
  }

  def extendContact(dlg: DialogList.DialogUserView, users:Seq[Crm.User]):ContactInfo = {
    val user = users(dlg.to-1)
    ContactInfo(dlg.id, dlg.to, user.login, dlg.hasNew, dlg.lastMsgTime)
  }

}