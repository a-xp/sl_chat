package ru.shoppinglive

import java.util.logging.{Level, Logger}

import akka.actor.ActorSystem
import akka.agent.Agent
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.{ActorMaterializer, Materializer}
import kamon.Kamon
import ru.shoppinglive.chat.admin_api.{CrmActor, CrmToken, MockCrmToken}
import ru.shoppinglive.chat.chat_api.{ClientNotifier, ConversationSupervisor, DialogsListSupervisor}
import ru.shoppinglive.chat.client_connection.ConnectionSupervisor
import ru.shoppinglive.chat.domain.{Crm, DialogHeader}
import ru.shoppinglive.chat.server.Router
import scaldi.{Injectable, Module, TypesafeConfigInjector}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.io.StdIn

/**
  * Created by rkhabibullin on 06.12.2016.
  */
object WebServer extends App with Injectable{
  Kamon.start()

  implicit private var container = new Module {
    bind [ActorSystem] toNonLazy ActorSystem("main") destroyWith (_.terminate())
    bind [ExecutionContext] to inject [ActorSystem]   .dispatcher
    bind [Materializer] to ActorMaterializer()(inject [ActorSystem])
    binding identifiedBy 'usersDb toNonLazy Agent[Seq[Crm.User]](Seq.empty)(inject [ExecutionContext])
    binding identifiedBy 'groupsDb toNonLazy Agent[Seq[Crm.Group]](Seq.empty)(inject [ExecutionContext])
    binding identifiedBy 'dialogsDb toNonLazy Agent[Map[Int,DialogHeader]](Map.empty)(inject [ExecutionContext])

    binding identifiedBy 'notifier toNonLazy inject [ActorSystem] .actorOf(ClientNotifier.props, "notifier")
    binding identifiedBy 'connections toNonLazy inject [ActorSystem]   .actorOf(ConnectionSupervisor.props, "connections")
    binding identifiedBy 'crm toNonLazy inject [ActorSystem]   .actorOf(CrmActor.props, "crm")
    binding identifiedBy 'dialogs toNonLazy inject [ActorSystem]    .actorOf(ConversationSupervisor.props, "dialogs")
    binding identifiedBy 'contacts toNonLazy inject [ActorSystem]   .actorOf(DialogsListSupervisor.props, "contacts")
    binding identifiedBy 'auth toNonLazy inject [ActorSystem]    .actorOf(inject[String]("chat.auth.env") match {
      case "MOCK" => MockCrmToken.props
      case _ => CrmToken.props
    }, "auth")
  } :: TypesafeConfigInjector()


  implicit val system = inject [ActorSystem]
  implicit val ec = inject [ExecutionContext]
  implicit val materializer = inject [Materializer]

  val port = 9100
  val route = DebuggingDirectives.logRequestResult("Client ReST", Logging.DebugLevel)((new Router).handler)
  val binding = Http().bindAndHandle(interface = "0.0.0.0", port=port, handler = route)
  println(s"Listening for connections on port $port...")

  StdIn.readLine()
  binding.flatMap(_.unbind()).onComplete(_ => {system.terminate(); Kamon.shutdown()})

}
