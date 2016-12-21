package ru.shoppinglive

import akka.actor.ActorSystem
import akka.agent.Agent
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import ru.shoppinglive.chat.admin_api.{CrmActor, CrmToken}
import ru.shoppinglive.chat.chat_api.{ClientNotifier, ConversationSupervisor, ConversationsList}
import ru.shoppinglive.chat.client_connection.ConnectionSupervisor
import ru.shoppinglive.chat.domain.Crm
import ru.shoppinglive.chat.server.Router
import scaldi.{Injectable, Module, TypesafeConfigInjector}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.io.StdIn

/**
  * Created by rkhabibullin on 06.12.2016.
  */
object WebServer extends App with Injectable{

  implicit private val container = new Module {
    bind [ActorSystem] toNonLazy ActorSystem("main") destroyWith (_.terminate())
    bind [ExecutionContext] to inject [ActorSystem]   .dispatcher
    bind [Materializer] to ActorMaterializer()(inject [ActorSystem])
    binding identifiedBy 'usersDb toNonLazy Agent[Seq[Crm.User]](Seq.empty)(inject [ExecutionContext])
    binding identifiedBy 'groupsDb toNonLazy Agent[Seq[Crm.Group]](Seq.empty)(inject [ExecutionContext])

    binding identifiedBy 'notifier toNonLazy inject [ActorSystem] .actorOf(ClientNotifier.props, "notifier")
    binding identifiedBy 'connections toNonLazy inject [ActorSystem]   .actorOf(ConnectionSupervisor.props, "connections")
    binding identifiedBy 'crm toNonLazy inject [ActorSystem]   .actorOf(CrmActor.props, "crm")
    binding identifiedBy 'auth toNonLazy inject [ActorSystem]   .actorOf(CrmToken.props, "auth")
    binding identifiedBy 'chat toNonLazy inject [ActorSystem]    .actorOf(ConversationSupervisor.props, "dialogs")
    binding identifiedBy 'chatList toNonLazy inject [ActorSystem]   .actorOf(ConversationsList.props, "dialogs_list")
  } :: TypesafeConfigInjector()

  implicit val system = inject [ActorSystem]
  implicit val ec = inject [ExecutionContext]
  implicit val materializer = inject [Materializer]

  val port = 9100
  val binding = Http().bindAndHandle(interface = "0.0.0.0", port=port, handler = (new Router).handler )
  println(s"Listening for connections on port $port...")

  StdIn.readLine()
  binding.flatMap(_.unbind()).onComplete(_=>system.terminate())

}
