package ru.shoppinglive

import java.util.Date

import akka.actor.Actor

/**
  * Created by rkhabibullin on 06.12.2016.
  */
object ChatService {
  trait ChatRole
  case class UserRole() extends ChatRole{
    val id = "user"
    val name = "Оператор"
  }
  case class AdminRole() extends ChatRole{
    val id = "admin"
    val name = "Супервайзер"
  }

  trait Cmd {var id=0}
  case class TokenCmd(token:String) extends Cmd
  case class BroadcastCmd(group: Int, msg:String) extends Cmd
  case class OpenDlgCmd(contact: Int) extends Cmd
  case class ReadCmd(contact: Int) extends Cmd
  case class TypingCmd() extends Cmd
  case class ConnectedCmd(client: Int, dir:ClientConnection.Direction) extends Cmd
  case class DisconnectedCmd() extends Cmd
  case class CmdProcessed(id: Int)

  trait Result
  case class InfoResult(role: String, roleName: String) extends Result
  case class GroupResult(id:Int, name:String) extends Result
  case class GroupsResult(groups:Seq[GroupResult]) extends Result
  case class ContactResult(id:Int, name: String, hasNew: Boolean, last: String) extends Result
  case class ContactsResult(contacts: Seq[ContactResult]) extends Result
  case class DialogResult(id:Int, login:String) extends Result


  class ContactSupervizor extends Actor {

  }

  class ContactActor extends Actor {
    
  }

}
