package ru.shoppinglive.chat.client_connection

import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import akka.event.LoggingReceive
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, WatermarkRequestStrategy}
import org.json4s.native.Serialization
import ru.shoppinglive.chat.chat_api.ConversationSupervisor.Cmd
import ru.shoppinglive.chat.client_connection.Connection.RecieverRdy

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class Reciever(val master:ActorRef) extends ActorSubscriber with ActorLogging {
  override def preStart(): Unit = {
    super.preStart()
    master ! RecieverRdy
  }
  override val requestStrategy = new WatermarkRequestStrategy(3)
  override def receive: Receive = LoggingReceive {
    case OnNext(tm:TextMessage) =>
      tmToCmd(tm) match {
        case cmd:Cmd => master ! cmd
      }
    case OnComplete => self ! PoisonPill
    case OnError => self ! PoisonPill
  }

  def tmToCmd(tm:TextMessage):Cmd = {
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization.read
    import ru.shoppinglive.chat.chat_api.ConversationSupervisor._
    implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[CreateDlgCmd], classOf[TokenCmd], classOf[BroadcastCmd], classOf[ReadCmd], classOf[MsgCmd], classOf[TypingCmd])))
    read[Cmd](tm.getStrictText)
  }
}

object Reciever {

  def props(master:ActorRef) = Props(new Reciever(master))
}
