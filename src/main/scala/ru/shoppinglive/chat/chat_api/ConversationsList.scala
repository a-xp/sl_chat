package ru.shoppinglive.chat.chat_api

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.agent.Agent
import akka.event.LoggingReceive
import akka.persistence.{Persistence, PersistentActor}
import ru.shoppinglive.chat.chat_api.ClientNotifier.AddressedMsg
import ru.shoppinglive.chat.chat_api.Cmd._
import ru.shoppinglive.chat.chat_api.ConversationSupervisor.{DialogInfo, ResetDialog}
import ru.shoppinglive.chat.chat_api.ConversationsList.ResetData
import ru.shoppinglive.chat.chat_api.Event.{DialogCreated, MsgConsumed, MsgPosted}
import ru.shoppinglive.chat.chat_api.Result._
import ru.shoppinglive.chat.domain.Crm.Admin
import ru.shoppinglive.chat.domain.{Crm, Dialog, DialogList}
import scaldi.{Injectable, Injector}


/**
  * Created by rkhabibullin on 13.12.2016.
  */
class ConversationsList(implicit inj:Injector) extends PersistentActor with ActorLogging with Injectable{
  private val usersDb = inject [Agent[Seq[Crm.User]]] ('usersDb)
  private val groupsDb = inject [Agent[Seq[Crm.Group]]] ('groupsDb)
  private val api = new DialogList
  private var maxId = 0

  override def receiveRecover: Receive = LoggingReceive {
    case DialogCreated(_, users, id) => api.create(id, users)
      maxId = maxId max id
      inject [ActorRef] ('chat) ! DialogInfo(id, users)
  }

  override def receiveCommand: Receive = LoggingReceive {
    case AuthenticatedCmd(fromUser, cmd, replyTo) => cmd match {
      case GetContacts() => val user = getUser(fromUser)
        replyTo ! ContactsResult(extendContactsList(user, api.listForUser(fromUser), usersDb()))
        if(user.role==Admin){
          replyTo ! getGroupsList(groupsDb())
        }
      case FindOrCreateDlgCmd(withWhom) if fromUser!=withWhom => val users = Set(fromUser, withWhom)
        val id = findDlg(users)
        inject [ActorRef] ('chat) ! DialogInfo(id, users)
        replyTo ! ContactUpdate(Result.extendContact(api.getUserView(id, fromUser), getUser(withWhom)))
      case BroadcastCmd(group, msg) => usersDb().filter(_.groups.contains(group)).map(_.id).filter(_!=fromUser).foreach{
        toUser => val users = Set(toUser, fromUser)
          val id = findDlg(users)
          inject [ActorRef] ('chat) ! DialogInfo(id, users)
          inject [ActorRef] ('chat) ! AuthenticatedCmd(fromUser, MsgCmd(id, msg), replyTo)
      }
      case TypingCmd(dlgId) =>
        api.getOthers(dlgId, fromUser) foreach(uid => inject [ActorRef] ('notifier) ! AddressedMsg(uid, TypingNotification(dlgId, fromUser)))
      case _ =>
    }
    case MsgPosted(dlgId,time,from,text) => api.newMsg(dlgId, from, time)
      api.getOthers(dlgId, from) foreach(uid => inject [ActorRef] ('notifier) ! AddressedMsg(uid, ContactUpdate(Result.extendContact(api.getUserView(dlgId, uid), getUser(from)))))
      inject [ActorRef] ('notifier) ! AddressedMsg(from, DialogNewMsg(dlgId, List(Dialog.Msg(text, time, from))))
    case MsgConsumed(dlgId,_,who) => api.acceptedMsg(dlgId, who)
      val view = api.getUserView(dlgId, who)
      inject [ActorRef] ('notifier) ! AddressedMsg(who, ContactUpdate(Result.extendContact(view, getUser(view.to))))
    case ResetData => deleteMessages(Long.MaxValue)
      api.list foreach(dlg => inject [ActorRef] ('chat) ! ResetDialog(dlg.id))
  }

  private def findDlg(users:Set[Int]):Int = {
    api.findForUsers(users) match {
      case Some(u) => u.id
      case None => maxId+=1; persist(DialogCreated(System.currentTimeMillis(), users, maxId)){_=>}
        api.create(maxId, users)
        maxId
    }
  }

  override def persistenceId: String = "dlg-list"

  def extendContactsList(user:Crm.User, dialogs: Seq[DialogList.DialogUserView], users: Seq[Crm.User]): Seq[ContactInfo] ={
    (if(user.role==Crm.Admin){
      users
    } else{
      users filter (_.role == Crm.Admin)
    }) filter(_.id!=user.id) map { other => dialogs.find(_.to==other.id).map(dlg =>
      ContactInfo(dlg.id, dlg.to, other.login, dlg.hasNew, dlg.lastMsgTime)).getOrElse(
      ContactInfo(0, other.id, other.login, false, 0)
    )}
  }

  def getGroupsList(groups: Seq[Crm.Group]) : GroupsResult = {
    GroupsResult(groups map(grp=> GroupInfo(grp.id, grp.name)))
  }

  private def getUser(id:Int): Crm.User ={
    usersDb()(id-1)
  }
}


object ConversationsList {
  def props(implicit inj:Injector) = Props(new ConversationsList)

  case object ResetData
}