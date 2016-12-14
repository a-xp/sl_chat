package ru.shoppinglive.chat.domain

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString


/**
  * Created by rkhabibullin on 13.12.2016.
  */
object Crm {
  sealed trait Role{
    val code:String
    val name:String
  }
  case object Admin extends Role{
    val code = "admin"
    val name = "Супервайзер"
  }
  case object Operator extends Role{
    val code = "user"
    val name = "Оператор"
  }

  case class User(id:Int, name:String, lastName:String, role: Role, groups: Set[Int], login:String, crmId:Int, active:Boolean=true)
  case class Group(id:Int, name: String, active:Boolean=true)

  case object RoleSerializer extends CustomSerializer[Role](format => ( {
    case JString("admin") => Admin
    case JString("user") => Operator
  }, {
    case r:Role => JString(r.code)
  }))

}

class Crm {
  import Crm._

  private var groups = Vector.empty[Group]
  private var users = Vector.empty[User]

  def addGroup(name:String) = {
    if(name!="" && !groups.exists(_.name==name)) {
      val grp = Group(groups.size + 1, name)
      groups = groups :+ grp
      Some(grp)
    }else{
      None
    }
  }
  def addUser(name:String, lastName:String, id:Int, role:Role, login:String) = {
    if(name!="" && login!="" && id>0 && !users.exists(_.crmId==id)){
      val user = User(users.size+1, name, lastName, role, Set.empty, login, id)
      users = users :+ user
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

  def getUser(id:Int) = if(users.size>=id)Some(users(id-1)) else None
  def getGroup(id:Int) = if(groups.size>=id)Some(groups(id-1)) else None

}
