package ru.shoppinglive.chat.domain

import scala.collection.mutable

/**
  * Created by rkhabibullin on 09.12.2016.
  */
object DialogList {
  case class Dialog(id:Int, users: Set[Int], newFor: Set[Int], lastMsgTime: Long)
  case class DialogUserView(id:Int, to:Int, hasNew:Boolean, lastMsgTime: Long)
}

class DialogList {
  import DialogList._

  private var dialogs = mutable.Map.empty[Int, DialogList.Dialog]

  def create(id:Int, users:Set[Int]):Unit = {
    val dlg = Dialog(id, users, Set.empty[Int], 0)
    dialogs(id) = dlg
  }

  def findForUsers(ids:Set[Int]):Option[DialogList.Dialog] = {
    dialogs.values find(_.users==ids)
  }

  def newMsg(id:Int, from:Int, time:Long):Unit = {
    val dlg = dialogs(id)
    dialogs(id) =  dlg.copy(newFor = dlg.users-from, lastMsgTime = time)
  }

  def getUserView(dlgId:Int, userId:Int):DialogUserView = {
    val dlg = dialogs(dlgId)
    DialogUserView(dlg.id, dlg.users.find(_ != userId).get, dlg.newFor.contains(userId), dlg.lastMsgTime)
  }

  def listForUser(id:Int): Seq[DialogUserView] = {
    dialogs.values.filter(_.users contains id).map(dlg => DialogUserView(dlg.id, dlg.users.find(_!=id).get, dlg.newFor.contains(id), dlg.lastMsgTime)).toSeq
  }

  def acceptedMsg(dlgId:Int, user:Int):Unit = {
    val dlg = dialogs(dlgId)
    dialogs(dlgId) = dlg.copy(newFor = dlg.newFor - user)
  }

  def get(id:Int):DialogList.Dialog = {
    dialogs(id)
  }

  def getOthers(dlgId:Int, userId:Int):Set[Int] = {
    dialogs(dlgId).users.filter(_!=userId)
  }

  def list:Iterable[DialogList.Dialog] = dialogs.values
}
