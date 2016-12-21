package ru.shoppinglive.chat.client_connection

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import scaldi.{Injectable, Injector}

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class ConnectionSupervisor(implicit inj:Injector) extends Actor with ActorLogging with Injectable{
  import ConnectionSupervisor._
  var lastId = 0

  override def receive: Receive = LoggingReceive {
    case cmd @ CreateConnection => lastId+=1; context.actorOf(Connection.props, "connection-"+lastId) forward cmd
  }

}

object ConnectionSupervisor {

  case object CreateConnection

  def props(implicit inj:Injector) = Props(new ConnectionSupervisor)
}
