package ru.shoppinglive.chat.client_connection

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class ConnectionSupervisor extends Actor with ActorLogging {
  import ConnectionSupervisor._

  override def receive: Receive = LoggingReceive {
    case cmd @ CreateConnection => context.actorOf(Connection.props) forward cmd
  }

}

object ConnectionSupervisor {

  case object CreateConnection

  def props = Props(new ConnectionSupervisor)
}
