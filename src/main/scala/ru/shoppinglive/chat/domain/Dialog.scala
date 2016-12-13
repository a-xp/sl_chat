package ru.shoppinglive.chat.domain

/**
  * Created by rkhabibullin on 09.12.2016.
  */
object Dialog{
  case class Msg(text:String, time:Long, from:Int)

}

class Dialog(val id:Int) {
  import Dialog._
  var state = List.empty[Msg]

  def newMsg(msg:Msg):Unit = {
     state = msg :: state
  }
  def read(from:Int, to:Int):List[Msg] = state.slice(from, from+to)

  def total = state.size
}
