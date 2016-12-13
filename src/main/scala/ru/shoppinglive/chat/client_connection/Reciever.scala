package ru.shoppinglive.chat.client_connection

import akka.actor.{ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, WatermarkRequestStrategy}
import org.json4s.native.Serialization

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class Reciever(val master:ActorRef) extends ActorSubscriber with ActorLogging {
  import ChatService.{Cmd,DisconnectedCmd}
  override def preStart(): Unit = {
    super.preStart()
    master ! "input rdy"
  }
  override val requestStrategy = new WatermarkRequestStrategy(3)
  override def receive: Receive = LoggingReceive {
    case OnNext(tm:TextMessage) =>
      tmToCmd(tm) match {
        case cmd:Cmd => master ! cmd
      }
    case OnComplete => master ! DisconnectedCmd
    case OnError => master ! DisconnectedCmd
  }
}

object Reciever {

  def tmToCmd(tm:TextMessage):ChatService.Cmd = {
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization.read
    import ChatService._
    implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[TokenCmd], classOf[BroadcastCmd], classOf[OpenDlgCmd], classOf[ReadCmd], classOf[MsgCmd], classOf[TypingCmd])))
    read[Cmd](tm.getStrictText)
  }

}
