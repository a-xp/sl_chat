package ru.shoppinglive.chat.chat_api

import akka.actor.ActorRef

/**
  * Created by rkhabibullin on 14.12.2016.
  */
object Cmd {
  case class TokenCmd(token:String) extends Cmd
  case class BroadcastCmd(group: Int, msg:String) extends Cmd
  case class CreateDlgCmd(withWhom:Int) extends Cmd
  case class FindOrCreateDlgCmd(withWhom:Int) extends Cmd
  case class ReadCmd(dlgId: Int, from:Int=0, to:Int=5) extends Cmd
  case class TypingCmd(dlgId: Int) extends Cmd
  case class MsgCmd(dlgId:Int, msg:String) extends Cmd
  case class ConnectedCmd() extends Cmd
  case class DisconnectedCmd() extends Cmd
}

sealed trait Cmd
