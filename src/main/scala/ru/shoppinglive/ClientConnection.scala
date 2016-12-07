package ru.shoppinglive


import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{ActorPublisher, ActorSubscriber, MaxInFlightRequestStrategy, RequestStrategy}
import org.json4s.native.Serialization

import scala.annotation.tailrec

/**
  * Created by rkhabibullin on 06.12.2016.
  */
object ClientConnection {

  case object ResultRejected
  case object ResultAccepted
  case class ClientAuthenticated(id: Int)

  trait Direction
  case object In extends Direction
  case object Out extends Direction

  class SenderActor extends ActorPublisher[Message] {
    val maxSize = 100
    var buf = Vector.empty[Message]
    var clientId = 0

    override def receive:Unit = {
      case result:ChatService.Result if buf.size==maxSize => sender() ! ResultRejected
      case result:ChatService.Result => sender() ! ResultAccepted
        val msg = resultToTm(result)
        if(buf.isEmpty && totalDemand>0) onNext(msg)
        else{
          buf:+=msg
        }
      case Request(_) => sendFromBuf()
      case Cancel => context.system.actorSelection("/user/contacts") ! ChatService.DisconnectedCmd
                  context.stop(self)
      case ClientAuthenticated(id) => clientId = id
        context.system.actorSelection("/user/contacts") ! ChatService.ConnectedCmd(id, Out)
    }

    @tailrec final def sendFromBuf(): Unit =
      if (totalDemand > 0) {
        if (totalDemand <= Int.MaxValue) {
          val (use, keep) = buf.splitAt(totalDemand.toInt)
          buf = keep
          use foreach onNext
        } else {
          val (use, keep) = buf.splitAt(Int.MaxValue)
          buf = keep
          use foreach onNext
          sendFromBuf()
        }
      }
  }

  class RecieverActor extends ActorSubscriber {
    val maxQueueSize = 20
    var cmdId = 0
    var queue = Map.empty[Int, ChatService.Cmd]
    override val requestStrategy = new MaxInFlightRequestStrategy(maxQueueSize) {
      override def inFlightInternally:Int = queue.size
    }
    override def receive: Receive = {
      case OnNext(tm:TextMessage) =>
        var cmd = tmToCmd(tm)
        cmd.id = {cmdId+=1; cmdId}
        queue += (cmdId -> cmd)
        context.system.actorSelection("/user/contacts") ! cmd
      case ChatService.CmdProcessed(id) => queue -= id
    }
  }

  def tmToCmd(tm:TextMessage):ChatService.Cmd = {
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization.read
    import ChatService._
    implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[TokenCmd], classOf[BroadcastCmd], classOf[OpenDlgCmd], classOf[ReadCmd])))
    read[Cmd](tm.getStrictText)
  }

  def resultToTm(result:ChatService.Result):TextMessage = {
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization.write
    import ChatService._
    implicit val formats =Serialization.formats(ShortTypeHints(List(classOf[InfoResult], classOf[GroupResult],
      classOf[GroupsResult], classOf[DialogResult], classOf[ContactResult], classOf[ContactsResult])))
    TextMessage(write[Result](result))
  }

}
