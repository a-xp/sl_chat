package ru.shoppinglive

import akka.actor.ActorSystem
import akka.agent.Agent
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import ru.shoppinglive.chat.admin_api.{CrmActor, CrmToken}
import ru.shoppinglive.chat.chat_api.{ClientNotifier, ConversationSupervisor, ConversationsList}
import ru.shoppinglive.chat.client_connection.ConnectionSupervisor
import ru.shoppinglive.chat.domain.Crm
import ru.shoppinglive.chat.server.Router
import scaldi.{Module, TypesafeConfigInjector}

import scala.io.StdIn

/**
  * Created by rkhabibullin on 06.12.2016.
  */
object WebServer extends App{

  implicit private val container = new Module {
    bind [ActorSystem] toNonLazy ActorSystem("main") destroyWith (_.terminate())
    binding identifiedBy 'usersDb toNonLazy Agent[Seq[Crm.User]](Seq.empty)
    binding identifiedBy 'groupsDb toNonLazy Agent[Seq[Crm.Group]](Seq.empty)

    binding identifiedBy 'notifier toNonLazy inject [ActorSystem] .actorOf(ClientNotifier.props)
    binding identifiedBy 'connections toNonLazy inject [ActorSystem]   .actorOf(ConnectionSupervisor.props)
    binding identifiedBy 'crm toNonLazy inject [ActorSystem]   .actorOf(CrmActor.props)
    binding identifiedBy 'auth toNonLazy inject [ActorSystem]   .actorOf(CrmToken.props)
    binding identifiedBy 'chat toNonLazy inject [ActorSystem]    .actorOf(ConversationSupervisor.props)
    binding identifiedBy 'chatList toNonLazy inject [ActorSystem]   .actorOf(ConversationsList.props)
  } :: new TypesafeConfigInjector

  implicit val system = ActorSystem("main")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val port = 9100
  val binding = Http().bindAndHandle(interface = "0.0.0.0", port=port, handler = (new Router).handler )
  println(s"Listening for connections on port $port...")

  StdIn.readLine()
  binding.flatMap(_.unbind()).onComplete(_=>system.terminate())

}
