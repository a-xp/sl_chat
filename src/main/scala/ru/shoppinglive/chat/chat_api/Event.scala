package ru.shoppinglive.chat.chat_api

/**
  * Created by rkhabibullin on 16.12.2016.
  */
object Event {
  case class DialogCreated(eId:Int, time:Long, users:Set[Int], id:Int) extends Event
  case class MsgPosted(eId:Int, dlgId:Int, time:Long, from:Int, msg:String) extends Event
  case class MsgConsumed(eId:Int, dlgId:Int, time:Long, who:Int) extends Event
}

sealed trait Event