package ru.shoppinglive.chat.client_connection

import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import org.json4s.native.Serialization
import ru.shoppinglive.chat.chat_api.ConversationSupervisor.Result
import ru.shoppinglive.chat.client_connection.Connection.SenderRdy

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class Sender(val master:ActorRef) extends ActorPublisher[Message] with ActorLogging {
  val maxSize = 20
  var buf = Vector.empty[Message]

  override def preStart(): Unit = {
    super.preStart()
    master ! SenderRdy
  }

  override def receive:Receive = LoggingReceive {
    case result:Result if buf.size==maxSize =>
    case result:Result =>
      val msg = resultToTm(result)
      if(buf.isEmpty && totalDemand>0) onNext(msg)
      else{ buf:+=msg }
    case Request(_) => sendFromBuf()
    case Cancel => self ! PoisonPill
  }

  def resultToTm(result:Result):TextMessage = {
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization.write
    import ru.shoppinglive.chat.chat_api.ConversationSupervisor._
    implicit val formats =Serialization.formats(ShortTypeHints(List(classOf[AuthSuccessResult], classOf[AuthFailedResult],
      classOf[GroupsResult], classOf[ContactsResult], classOf[ContactUpdate], classOf[DialogNewMsg], classOf[DialogMsgList],
      classOf[TypingNotification])))
    TextMessage(write[Result](result))
  }

  final def sendFromBuf(): Unit =
    if (totalDemand > 0) {
      val (use, keep) = buf.splitAt(totalDemand min Int.MaxValue toInt)
      buf = keep
      use foreach onNext
    }
}

object Sender {
  def props(master:ActorRef) = Props(new Sender(master))

}
