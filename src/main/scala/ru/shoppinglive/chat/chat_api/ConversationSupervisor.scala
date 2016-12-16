package ru.shoppinglive.chat.chat_api

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.agent.Agent
import akka.contrib.persistence.mongodb.{MongoReadJournal, ScalaDslMongoReadJournal}
import akka.event.LoggingReceive
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import ru.shoppinglive.chat.chat_api.Result._
import ru.shoppinglive.chat.domain.{Crm, DialogList}

import scala.collection.mutable

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class ConversationSupervisor(val usersDb: Agent[Seq[Crm.User]], val groupDb: Agent[Seq[Crm.Group]])  extends Actor with ActorLogging {
  import ConversationSupervisor._
  import ru.shoppinglive.chat.chat_api.Cmd._
  private val clients = mutable.Map.empty[Int, Seq[ActorRef]]
  private val conversations = mutable.Map.empty[Int, ActorRef]
  val api = new DialogList

  implicit val materializer = ActorMaterializer()
  val readJournal = PersistenceQuery(context.system).readJournalFor[ScalaDslMongoReadJournal](MongoReadJournal.Identifier)
  readJournal.allPersistenceIds().runForeach(println(_))

  override def receive: Receive = LoggingReceive {
    case athcmd @ AuthenticatedCmd(from, cmd, replyTo) => cmd match {
      case ConnectedCmd() =>
        clients(from) = clients.getOrElseUpdate(from, mutable.Seq.empty[ActorRef]) :+ replyTo
        replyTo ! ContactsResult(extendContactsList(from, api.listForUser(from), usersDb()))
        mapGroupsList(usersDb()(from-1), groupDb()) foreach( replyTo ! _)
      case DisconnectedCmd() => clients(from) = clients(from).filter(_ != replyTo)
      case FindOrCreateDlgCmd(withWhom) =>
        val dlg = api.findOrCreate(Set(from, withWhom))
        val ntfCreator = ContactUpdate(extendContact(api.getUserView(dlg.id, from), usersDb()))
        clients(from) foreach(_ ! ntfCreator)
        val ntfOther = ContactUpdate(extendContact(api.getUserView(dlg.id, withWhom), usersDb()))
        clients.get(withWhom).foreach(_ foreach (_ ! ntfOther))
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
        conversations.getOrElseUpdate(dlgId, context.actorOf(Conversation.props(dlgId), "dialog-"+dlgId)) ! athcmd
      case TypingCmd(dlgId) => api.getOthers(dlgId, from) flatMap clients.get foreach( _ foreach (_ ! TypingNotification(dlgId, from)))
      case cmd @ MsgCmd(dlgId, _, _) =>
        val curTime=System.currentTimeMillis()
        api.newMsg(dlgId, from, curTime)
        conversations.getOrElseUpdate(dlgId, context.actorOf(Conversation.props(dlgId), "dialog-"+dlgId)) ! AuthenticatedCmd(from, cmd.copy(time=curTime), replyTo)
        clients(from) foreach(_ ! ContactUpdate(extendContact(api.getUserView(dlgId, from), usersDb())))
        api.getOthers(dlgId, from) foreach {
          otherId => clients.get(otherId) foreach(_ foreach(_ ! ContactUpdate(extendContact(api.getUserView(dlgId, otherId), usersDb()))))
        }
      case BroadcastCmd(group, msg) =>
        val curTime=System.currentTimeMillis()
        usersDb().filter(_.groups.contains(group)).filter(_.id!=from).foreach{
          to => val dlg = api.findOrCreate(Set(to.id, from))
            api.newMsg(dlg.id, from, curTime)
            conversations.getOrElseUpdate(dlg.id, context.actorOf(Conversation.props(dlg.id), "dialog-"+dlg.id)) ! AuthenticatedCmd(from, MsgCmd(dlg.id, msg, curTime), replyTo)
            clients.get(to.id) foreach(_ foreach(_ ! ContactUpdate(extendContact(api.getUserView(dlg.id, to.id), usersDb()))))
        }
      case _ =>
    }
  }

}

object ConversationSupervisor{

  case class AuthenticatedCmd(from:Int, cmd:Cmd, replyTo:ActorRef)

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

  def props(usersDb: Agent[Seq[Crm.User]], groupDb: Agent[Seq[Crm.Group]]) = Props(new ConversationSupervisor(usersDb, groupDb))
}