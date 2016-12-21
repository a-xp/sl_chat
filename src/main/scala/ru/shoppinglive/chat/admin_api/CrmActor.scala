package ru.shoppinglive.chat.admin_api

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.agent.Agent
import akka.event.LoggingReceive
import akka.persistence.PersistentActor
import ru.shoppinglive.chat.domain.Crm
import ru.shoppinglive.chat.domain.Crm._
import scaldi.{Injectable, Injector}
/**
  * Created by rkhabibullin on 13.12.2016.
  */
object CrmActor {

  sealed trait Cmd
  case class GroupAdd(name: String) extends Cmd
  case class UserAdd(name:String, lastName:String, id:Int, role:Role, login:String) extends Cmd
  case class UserSetRole(id:Int, role: Role) extends Cmd
  case class UserAddGroup(user:Int, group:Int) extends Cmd
  case class UserRemoveGroup(user:Int, group:Int) extends Cmd

  case class GetGroup(id:Int)
  case object GetGroups
  case object GetUsers
  case class GetUser(id:Int)

  case object ResultOK
  case object ResultFail

  def props = Props(new CrmActor)
}

class CrmActor(implicit inj:Injector) extends PersistentActor with ActorLogging with Injectable{
  private var api = new Crm
  private val usersDb = inject [Agent[Seq[Crm.User]]] ('usersDb)
  private val groupDb = inject [Agent[Seq[Crm.Group]]] ('groupsDb)
  import CrmActor._

  override def persistenceId = "crm-data"

  val receiveRecover: Receive = {
    case cmd:Cmd => processCmd(cmd)
  }

  val receiveCommand: Receive = LoggingReceive {
    case cmd:Cmd =>  persist(cmd){ sender ! processCmd(_)}
    case GetGroups => sender ! api.getGroups
    case GetUsers => sender ! api.getUsers
    case GetUser(id) => sender ! api.getUser(id).getOrElse(ResultFail)
    case GetGroup(id) => sender ! api.getGroup(id).getOrElse(ResultFail)
  }

  def processCmd(cmd:Cmd):Any = {
    cmd match {
      case GroupAdd(name) => api.addGroup(name) match {
        case Some(grp) => groupDb.send(api.getGroups)
          grp
        case _ => ResultFail
      }
      case UserAdd(name, lastName, id, role, login) => api.addUser(name, lastName, id, role, login) match {
        case Some(u) => usersDb.send(api.getUsers)
           u
        case _ => ResultFail
      }
      case UserSetRole(id, role) => ResultFail
      case UserAddGroup(user, group) => api.addUserToGroup(user, group) match {
        case Some(u) => usersDb.send(api.getUsers)
          u
        case _ => ResultFail
      }
      case UserRemoveGroup(user, group) => api.removeUserFromGroup(user, group) match {
        case Some(u) => usersDb.send(api.getUsers)
          u
        case _ => ResultFail
      }
    }
  }
}
