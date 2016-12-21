package ru.shoppinglive.chat.domain

/**
  * Created by rkhabibullin on 09.12.2016.
  */
object Dialog{
  case class Msg(text:String, time:Long, from:Int)

}

class Dialog(val id:Int, var users:Set[Int]) {
  import Dialog._

  private var newFor = Set.empty[Int]
  private var state = List.empty[Msg]
  def newMsg(msg:Msg):Unit = {
    state = msg :: state
    newFor = users - msg.from
  }
  def read(who:Int, from:Int, to:Int):List[Msg] = {
    newFor -= who
    state.slice(from, from+to)
  }
  def consume(who:Int):Unit = newFor -= who
  def hasNew(who:Int):Boolean = newFor.contains(who)
  def total:Int = state.size
}
