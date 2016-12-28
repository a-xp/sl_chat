package ru.shoppinglive.chat.admin_api

import akka.actor.{Actor, ActorLogging, Props}
import akka.actor.Actor.Receive
import akka.agent.Agent
import akka.event.LoggingReceive
import ru.shoppinglive.chat.admin_api.CrmToken.{AuthFailed, AuthSuccess}
import ru.shoppinglive.chat.chat_api.Cmd.TokenCmd
import ru.shoppinglive.chat.domain.Crm
import ru.shoppinglive.chat.domain.Crm.User
import scaldi.{Injectable, Injector}

/**
  * Created by 1 on 25.12.2016.
  */
class MockCrmToken(implicit inj:Injector) extends Actor with ActorLogging with Injectable{
  private val usersDb = inject [Agent[Seq[Crm.User]]] ('usersDb)

  override def receive: Receive = LoggingReceive {
    case TokenCmd(token) =>
      usersDb() find (_.crmId==token.toInt) match {
        case Some(u) => sender ! AuthSuccess(u)
        case None => sender ! AuthFailed
      }
  }
}

object MockCrmToken {
  def props(implicit inj:Injector) = Props(new MockCrmToken)

}