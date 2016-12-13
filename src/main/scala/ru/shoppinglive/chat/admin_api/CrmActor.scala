package ru.shoppinglive.chat.admin_api

import akka.actor.{Actor, ActorLogging, Props}
import akka.agent.Agent
import akka.event.LoggingReceive
import ru.shoppinglive.chat.domain.Crm
import ru.shoppinglive.chat.domain.Crm._
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

  def props(usersDb: Agent[Seq[Crm.User]], groupDb: Agent[Seq[Crm.Group]]) = Props(new CrmActor(usersDb, groupDb))
}

class CrmActor(val usersDb: Agent[Seq[Crm.User]], val groupDb: Agent[Seq[Crm.Group]]) extends Actor with ActorLogging{
  var api = new Crm
  import CrmActor._

  override def receive: Receive = LoggingReceive {
    case GroupAdd(name) => api.addGroup(name) foreach(_ => groupDb.send(api.getGroups))
    case UserAdd(name, lastName, id, role, login) => api.addUser(name, lastName, id, role, login) foreach(_=> usersDb.send(api.getUsers))
    case UserSetRole(id, role) =>
    case UserAddGroup(user, group) => api.addUserToGroup(user, group) foreach(_=> usersDb.send(api.getUsers))
    case UserRemoveGroup(user, group) => api.removeUserFromGroup(user, group) foreach(_=> usersDb.send(api.getUsers))
  }

}
