package ru.shoppinglive.chat.chat_api

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.agent.Agent
import akka.event.LoggingReceive
import akka.persistence._
import ru.shoppinglive.chat.chat_api.ClientNotifier.AddressedMsg
import ru.shoppinglive.chat.chat_api.Cmd.{AuthenticatedCmd, GetContacts}
import ru.shoppinglive.chat.chat_api.DialogsListActor.DialogState
import ru.shoppinglive.chat.chat_api.Event.{DialogCreated, MsgConsumed, MsgPosted}
import ru.shoppinglive.chat.chat_api.Result.{ContactChanges, ContactInfo, ContactUpdate, ContactsResult}
import ru.shoppinglive.chat.domain.{Crm, DialogHeader}
import ru.shoppinglive.chat.domain.Dialog.Msg
import scaldi.{Injectable, Injector}

import scala.collection.mutable

/**
  * Created by 1 on 31.12.2016.
  */
class DialogsListActor(val id:Int)(implicit inj:Injector) extends PersistentActor with ActorLogging with Injectable{
  private var state = mutable.Map.empty[Int, DialogState]
  private val usersDb = inject [Agent[Seq[Crm.User]]] ('usersDb)
  private val dialogsAgent = inject [Agent[Map[Int, DialogHeader]]] ('dialogsDb)
  private var events=0
  private val snapInterval = inject[Int]("chat.snapshot_interval")
  implicit private val ec = context.system.dispatcher

  override def persistenceId: String = "contacts-list-"+id
  override def receiveRecover: Receive = LoggingReceive {
    case MsgConsumed(dlgId, time, who) if who==id => state(dlgId) = getDlg(dlgId).copy(newMsg = 0)
    case MsgPosted(dlgId, time, from, _) if from==id => state(dlgId) = getDlg(dlgId).copy(last = time)
    case MsgPosted(dlgId, time, from, _) if from!=id => val curDlg = getDlg(dlgId)
      state(dlgId) = curDlg.copy(last = time, newMsg = curDlg.newMsg+1)
    case SnapshotOffer(metadata, snapshot) => state = mutable.Map(snapshot.asInstanceOf[Map[Int, DialogState]].toSeq: _*)
    case RecoveryCompleted =>
      import scala.concurrent.duration._
      if(snapInterval>0)context.system.scheduler.schedule(snapInterval.seconds, snapInterval.seconds, self, "snap")
  }

  override def receiveCommand: Receive = LoggingReceive {
    case AuthenticatedCmd(fromUser, GetContacts(), replyTo) =>
      val users = usersDb()
      val user = getUser(id)
      replyTo ! ContactsResult((if(user.role==Crm.Admin){
        users
      } else{
        users filter (_.role == Crm.Admin)
      }) filter(_.id!=user.id) map { other => state.values.find(_.withWhom==other.id).map(dlg =>
        ContactInfo(dlg.dlgId, dlg.withWhom, other.login, dlg.newMsg>0, dlg.last, other.name, other.lastName)).getOrElse(
        ContactInfo(0, other.id, other.login, false, 0, other.name, other.lastName)
      )})
    case e@MsgConsumed(dlgId, time, who) => persistAsync(e){_=>}
      if(who==id){
        val dlg = getDlg(dlgId).copy(newMsg = 0)
        state(dlgId) = dlg
        val withUser = getUser(dlg.withWhom)
        inject [ActorRef] ('notifier) ! AddressedMsg(id, ContactUpdate(ContactChanges(dlgId, dlg.withWhom, false, dlg.last)))
      }
    case e@MsgPosted(dlgId, time, from, _) => persistAsync(e){_=>}
      if(from==id){
        val dlg = getDlg(dlgId).copy(last = time)
        state(dlgId) = dlg
      }else{
        val curDlg = getDlg(dlgId)
        val dlg = curDlg.copy(last = time, newMsg = curDlg.newMsg+1)
        state(dlgId) = dlg
        val withUser = getUser(dlg.withWhom)
        inject [ActorRef] ('notifier) ! AddressedMsg(id, ContactUpdate(ContactChanges(dlgId, dlg.withWhom, true, dlg.last)))
      }
    case "reset" => deleteMessages(Long.MaxValue); deleteSnapshots(SnapshotSelectionCriteria.create(Long.MaxValue,Long.MaxValue))
      state = mutable.Map.empty[Int, DialogState]
    case "snap" => if(events>5)saveSnapshot(state.toMap)
    case SaveSnapshotSuccess(metadata) => deleteMessages(metadata.sequenceNr)
      deleteSnapshots(SnapshotSelectionCriteria.create(metadata.sequenceNr-1, Long.MaxValue))
  }

  private def getDlg(dlgId:Int):DialogState = {
    state.getOrElseUpdate(dlgId, DialogState(dlgId, dialogsAgent()(dlgId).between.find(_!=id).get, 0, 0))
  }
  private def getUser(id:Int): Crm.User ={
    usersDb()(id-1)
  }
}


object DialogsListActor {
  def props(id:Int)(implicit inj:Injector) = Props(new DialogsListActor(id))

  case class DialogState(dlgId:Int, withWhom:Int, newMsg:Int, last:Long)

}
