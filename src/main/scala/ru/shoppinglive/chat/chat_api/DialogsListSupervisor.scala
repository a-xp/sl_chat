package ru.shoppinglive.chat.chat_api

import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import akka.agent.Agent
import akka.event.LoggingReceive
import akka.persistence._
import ru.shoppinglive.chat.chat_api.ClientNotifier.AddressedMsg
import ru.shoppinglive.chat.chat_api.Cmd._
import ru.shoppinglive.chat.chat_api.Event.{DialogCreated, MsgConsumed, MsgPosted}
import ru.shoppinglive.chat.chat_api.Result.{DialogIdResult, TypingNotification}
import ru.shoppinglive.chat.domain.{Crm, DialogHeader}
import scaldi.{Injectable, Injector}

import scala.collection.mutable

/**
  * Created by 1 on 31.12.2016.
  */
class DialogsListSupervisor(implicit inj:Injector) extends PersistentActor with ActorLogging with Injectable{
  implicit private val ec = context.system.dispatcher
  private val dialogsAgent = inject [Agent[Map[Int, DialogHeader]]] ('dialogsDb)
  private val usersDb = inject [Agent[Seq[Crm.User]]] ('usersDb)
  private var state = mutable.Map.empty[Int, DialogHeader]
  private var maxId=0
  private val actors = mutable.Map.empty[Int,ActorRef]
  private var usedActors = Set.empty[Int]
  private var numEvents = 0
  private val snapInterval = inject[Int]("chat.snapshot_interval")
  private val unloadInterval = inject[Int]("chat.workers_unload_interval")

  override def receiveRecover: Receive = LoggingReceive {
    case DialogCreated(_, users, id) => state(id) = DialogHeader(id, users)
      maxId = maxId max id; numEvents+=1
    case SnapshotOffer(metadata, snapshot) => state = mutable.Map(snapshot.asInstanceOf[Map[Int, DialogHeader]].toSeq: _*)
      maxId = state.keys.max
    case RecoveryCompleted => dialogsAgent.send(state.toMap)
      import scala.concurrent.duration._
      if(unloadInterval>0)context.system.scheduler.schedule(unloadInterval.seconds, unloadInterval.seconds, self, "unload")
      if(snapInterval>0)context.system.scheduler.schedule(snapInterval.seconds, snapInterval.seconds, self, "snap")
  }

  override def persistenceId: String = "contacts-lists"

  override def receiveCommand: Receive = LoggingReceive {
    case SaveSnapshotSuccess(metadata) => deleteMessages(metadata.sequenceNr)
      deleteSnapshots(SnapshotSelectionCriteria.create(metadata.sequenceNr-1, Long.MaxValue))
      numEvents=0
    case "snap" =>if(numEvents>10)saveSnapshot(state.toMap)
    case "unload" => actors.keys.toSet.diff(usedActors) foreach{id => actors(id) ! PoisonPill}
      actors retain((id,_)=>usedActors.contains(id)); usedActors=Set.empty
    case acmd@AuthenticatedCmd(fromUser, cmd, replyTo) => cmd match {
      case FindOrCreateDlgCmd(withWhom) => if(withWhom!=fromUser){
          val users = Set(withWhom, fromUser)
          val dlgId = state.values.find(_.between==users).map(_.id).getOrElse{
            maxId+=1; state(maxId)=DialogHeader(maxId, users); numEvents+=1; dialogsAgent.send(state.toMap)
            persistAsync(DialogCreated(System.currentTimeMillis, users, maxId)){_=>}
            inject [ActorRef]('notifier) ! AddressedMsg(withWhom, DialogIdResult(fromUser, maxId))
            maxId
          }
          replyTo ! DialogIdResult(withWhom, dlgId)
        }
      case BroadcastCmd(group, text) => usersDb().filter(_.groups.contains(group)).map(_.id).filter(_!=fromUser).foreach{
        toUser => val users = Set(toUser, fromUser)
          val id = state.values.find(_.between==users).map(_.id).getOrElse{
            maxId+=1; state(maxId)=DialogHeader(maxId, users); numEvents+=1; dialogsAgent.send(state.toMap)
            persistAsync(DialogCreated(System.currentTimeMillis, users, maxId)){_=>}
            inject [ActorRef]('notifier) ! AddressedMsg(toUser, DialogIdResult(fromUser, maxId))
            replyTo ! DialogIdResult(toUser, maxId)
            maxId
          }
          inject [ActorRef] ('chat) ! AuthenticatedCmd(fromUser, MsgCmd(id, text), replyTo)
      }
      case TypingCmd(dlgId) => state(dlgId).between.filter(_!=fromUser) foreach(
        userId=> inject [ActorRef]('notifier) ! AddressedMsg(userId, TypingNotification(dlgId, fromUser)))
      case _ => sendToWorker(fromUser, acmd)
    }
    case cmd@MsgPosted(dlgId,_,_,_) => state(dlgId).between foreach(id=>sendToWorker(id, cmd))
    case cmd@MsgConsumed(dlgId,_,_) => state(dlgId).between foreach(id=>sendToWorker(id, cmd))
    case "reset" => throw new Exception("reset dlg lists supervisor")
  }

  private def sendToWorker(id:Int, msg:Any):Unit = {
    actors.getOrElseUpdate(id, context.actorOf(DialogsListActor.props(id), "contacts-list-"+id)) ! msg
    usedActors +=id
  }


}

object DialogsListSupervisor {

  def props(implicit inj:Injector) = Props(new DialogsListSupervisor)
}