package ru.shoppinglive.chat.chat_api

import akka.actor.{Actor, ActorRef, Props}
import akka.actor.Actor.Receive
import ru.shoppinglive.chat.chat_api.Cmd.{ConnectedCmd, DisconnectedCmd}
import ru.shoppinglive.chat.chat_api.ConversationSupervisor.AuthenticatedCmd
import ru.shoppinglive.chat.chat_api.Result.ContactsResult

import scala.collection.mutable

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class ConversationsList {

}


object ConversationsList {

}