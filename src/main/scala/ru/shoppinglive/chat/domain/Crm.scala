package ru.shoppinglive.chat.domain

import com.sun.javaws.exceptions.InvalidArgumentException

/**
  * Created by rkhabibullin on 13.12.2016.
  */
object Crm {
  sealed trait Role
  case object Admin extends Role
  case object Operator extends Role

  case class User(id:Int, name:String, lastName:String, role: Role, groups: Set[Int], login:String, crmId:Int)
  case class Group(id:Int, name: String)

}

class Crm {
  import Crm._

  var groups = Vector.empty[Group]
  var users = Vector.empty[User]

  def addGroup(name:String) = {
    if(name!="" && !groups.exists(_.name==name)) {
      val grp = Group(groups.size + 1, name)
      groups = groups.updated(groups.size, grp)
      Some(grp)
    }else{
      None
    }
  }
  def addUser(name:String, lastName:String, id:Int, role:Role, login:String) = {
    if(name!="" && login!="" && id>0 && !users.exists(_.crmId==id)){
      val user = User(users.size+1, name, lastName, role, Set.empty, login, id)
      users = users.updated(users.size, user)
      Some(user)
    }else{
      None
    }
  }
  def addUserToGroup(userId:Int, grpId:Int) = {
    if(groups.size>=grpId && users.size>=userId){
      val user = users(userId-1).copy(groups = users(userId-1).groups + grpId)
      users = users.updated(userId-1, user)
      Some(user)
    }else{
      None
    }
  }
  def removeUserFromGroup(userId:Int, grpId:Int) = {
    if(groups.size>=grpId && users.size>=userId){
      val user = users(userId-1).copy(groups = users(userId-1).groups - grpId)
      users = users.updated(userId-1, user)
      Some(user)
    }else{
      None
    }
  }

  def getGroups = groups
  def getUsers = users

}
