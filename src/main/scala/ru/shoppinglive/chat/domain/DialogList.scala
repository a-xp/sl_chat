package ru.shoppinglive.chat.domain
/**
  * Created by rkhabibullin on 09.12.2016.
  */
object DialogList {
  case class Dialog(id:Int, users: Set[Int], newFor: Set[Int], lastMsgTime: Long)
  case class DialogUserView(id:Int, to:Int, hasNew:Boolean, lastMsgTime: Long)
}

class DialogList {
  import DialogList._

  var list = Vector.empty[DialogList.Dialog]

  def create(users:Set[Int]):DialogList.Dialog = {
    val dlg = Dialog(list.size, users, Set.empty[Int], 0)
    dlg +: list
    dlg
  }

  def findForUsers(ids:Set[Int]):Option[DialogList.Dialog] = {
    list.find(_.users==ids)
  }

  def findOrCreate(ids:Set[Int]):DialogList.Dialog = {
    findForUsers(ids).getOrElse(create(ids))
  }

  def newMsg(id:Int, from:Int, time:Long):Unit = {
    val dlg = list(id)
    list = list.updated(id, dlg.copy(newFor = dlg.users-from, lastMsgTime = time))
  }

  def getUserView(dlgId:Int, userId:Int):DialogUserView = {
    val dlg = list(dlgId-1)
    DialogUserView(dlg.id, dlg.users.find(_ != userId).get, dlg.newFor.contains(userId), dlg.lastMsgTime)
  }

  def listForUser(id:Int): Seq[DialogUserView] = {
    list.filter(_.users contains id).map(dlg => DialogUserView(dlg.id, dlg.users.find(_!=id).get, dlg.newFor.contains(id), dlg.lastMsgTime))
  }

  def acceptedMsg(dlgId:Int, user:Int):Unit = {
    val dlg = list(dlgId)
    list = list.updated(dlgId, dlg.copy(newFor = dlg.newFor - user))
  }

  def get(id:Int):DialogList.Dialog = {
    list(id-1)
  }

  def getOthers(dlgId:Int, userId:Int) = {
    list(dlgId-1).users.filter(_!=userId)
  }

}
