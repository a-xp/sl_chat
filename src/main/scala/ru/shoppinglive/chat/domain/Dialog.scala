package ru.shoppinglive.chat.domain

import scala.collection.mutable

/**
  * Created by rkhabibullin on 09.12.2016.
  */
object Dialog{
  case class Msg(text:String, time:Long, from:Int)

}

class Dialog(val id:Int, var users:Set[Int]) {
  import Dialog._

  private val newFor = mutable.Map.empty[Int,Int]
  private var state = List.empty[Msg]
  def newMsg(msg:Msg):Unit = {
    state = msg :: state
    (users-msg.from) foreach(userId=>newFor(userId)=newFor.getOrElse(userId,0)+1)
  }
  def readNew(who:Int):List[Msg] = {
    val result = state.slice(0, newFor.getOrElse(who,0))
    newFor(who)=0
    result
  }
  def read(who:Int, from:Int, to:Int):List[Msg] = {
    newFor(who) = 0
    state.slice(from, from+to)
  }
  def consume(who:Int):Unit = newFor(who)=0
  def hasNew(who:Int):Boolean = newFor.getOrElse(who,0)>0
  def total:Int = state.size
}
