package ru.shoppinglive.chat.client_connection

import akka.actor.{ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import org.json4s.native.Serialization
import ru.shoppinglive.ChatService
import ru.shoppinglive.chat.client_connection.Sender.MsgRejected

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class Sender(val master:ActorRef) extends ActorPublisher[Message] with ActorLogging {
  val maxSize = 20
  var buf = Vector.empty[Message]
  import Connection._

  override def preStart(): Unit = {
    super.preStart()
    master ! SenderRdy()
  }

  override def receive:Receive = LoggingReceive {
    case result:ChatService.Result if buf.size==maxSize =>
    case result:ChatService.Result =>
      val msg = resultToTm(result)
      if(buf.isEmpty && totalDemand>0) onNext(msg)
      else{
        buf:+=msg
      }
    case Request(_) => sendFromBuf()
    case Cancel => context.system.actorSelection("/user/contacts") ! ChatService.DisconnectedCmd
      context.stop(self)
  }

  final def sendFromBuf(): Unit =
    if (totalDemand > 0) {
      val (use, keep) = buf.splitAt(totalDemand min Int.MaxValue toInt)
      buf = keep
      use foreach onNext
    }
}

object Sender {
  case class MsgRejected()

  def resultToTm(result:ChatService.Result):TextMessage = {
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization.write
    import ChatService._
    implicit val formats =Serialization.formats(ShortTypeHints(List(classOf[InfoResult], classOf[GroupResult],
      classOf[GroupsResult], classOf[DialogResult], classOf[ContactResult], classOf[ContactsResult])))
    TextMessage(write[Result](result))
  }

}
