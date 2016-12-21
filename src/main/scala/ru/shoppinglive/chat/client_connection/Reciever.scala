package ru.shoppinglive.chat.client_connection

import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import akka.event.{Logging, LoggingReceive}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, WatermarkRequestStrategy}
import org.json4s.native.Serialization
import ru.shoppinglive.chat.chat_api.Cmd
import ru.shoppinglive.chat.chat_api.Cmd._
import ru.shoppinglive.chat.client_connection.Connection.RecieverRdy

import scala.util.Try

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
        case Some(cmd:Cmd) => master ! cmd
        case None => log.warning("Unknow cmd recieved from client %s", tm)
      }
    case OnComplete => self ! PoisonPill
    case OnError => self ! PoisonPill
  }

  def tmToCmd(tm:TextMessage):Option[Cmd] = {
    import org.json4s.ShortTypeHints
    import org.json4s.native.Serialization.read
    implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[GetContacts], classOf[TokenCmd],
      classOf[BroadcastCmd], classOf[ReadCmd], classOf[MsgCmd], classOf[TypingCmd], classOf[FindOrCreateDlgCmd])))
    Try{read[Cmd](tm.getStrictText)}.toOption
  }
}

object Reciever {

  def props(master:ActorRef) = Props(new Reciever(master))
}
