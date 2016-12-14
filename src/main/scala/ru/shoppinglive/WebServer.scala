package ru.shoppinglive

import akka.actor.{ActorSystem, Props}
import akka.agent.Agent
import akka.http.javadsl.server.Route
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, UpgradeToWebSocket}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import ru.shoppinglive.chat.admin_api.{CrmActor, CrmToken}
import ru.shoppinglive.chat.chat_api.ConversationSupervisor
import ru.shoppinglive.chat.client_connection.ConnectionSupervisor
import ru.shoppinglive.chat.domain.Crm
import ru.shoppinglive.chat.domain.Crm.RoleSerializer

import scala.concurrent.{Await, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}

/**
  * Created by rkhabibullin on 06.12.2016.
  */
object WebServer extends App{
  final case class ContactResponse(login: String, id: Int, io:String)

  implicit val system = ActorSystem("main")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val usersDb = Agent[Seq[Crm.User]](Seq.empty)
  val groupsDb = Agent[Seq[Crm.Group]](Seq.empty)

  val contactSupervisor = system.actorOf(ConversationSupervisor.props(usersDb, groupsDb), "chat")
  val connectionSupervisor = system.actorOf(ConnectionSupervisor.props, "connections")
  val crmService = system.actorOf(CrmActor.props(usersDb, groupsDb), "crm")
  val authService = system.actorOf(CrmToken.props(usersDb), "auth")

  import akka.http.scaladsl.server.Directives._

  val wsHandler : HttpRequest=>Future[HttpResponse] = {
    case req @ HttpRequest(HttpMethods.GET, Uri.Path("/chat"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) =>
          import scala.concurrent.duration._
          pattern.ask(connectionSupervisor, ConnectionSupervisor.CreateConnection)(1 seconds) map {
            case (sink, source) =>
              upgrade.handleMessagesWithSinkSource(sink.asInstanceOf[Sink[Message, akka.actor.ActorRef]],
                source.asInstanceOf[Source[Message, akka.actor.ActorRef]])
            case _ => println("connection supervisor - invalid response")
              HttpResponse(StatusCodes.InternalServerError)
          }
        case None => Future.successful(HttpResponse(StatusCodes.BadRequest))
      }
    case r: HttpRequest =>
      r.discardEntityBytes()
      Future.successful(HttpResponse(StatusCodes.BadRequest))
  }

  import org.json4s.native.Serialization.{write,read}
  import scala.concurrent.duration._
  import ch.megard.akka.http.cors.CorsDirectives._
  implicit val formats = org.json4s.DefaultFormats + RoleSerializer
  implicit val timeout = Timeout(50.milliseconds)

  val apiRoute = cors() {
      pathPrefix("admin") {
        pathPrefix("group") {
          path(IntNumber) {
            id => get {
              complete(pattern.ask(crmService, CrmActor.GetGroup(id)).map {
                case grp:Crm.Group
                => HttpResponse(status =StatusCodes.OK, entity = write[Crm.Group](grp))
              } recover {
                case _ => HttpResponse(StatusCodes.NotFound)
              })
            } ~ put {
              complete("update group"+id)
            }
          } ~ pathEnd {
            post{
              extract(_.request){
                req =>
                  complete(Unmarshal(req.entity).to[String] map read[CrmActor.GroupAdd] flatMap {
                    pattern.ask(crmService, _) map {
                      case grp:Crm.Group => HttpResponse(StatusCodes.OK, entity = write[Crm.Group](grp))
                    }
                  } recover{
                    case _ => HttpResponse(StatusCodes.BadRequest)
                  })
              }
            } ~ get {
              complete(pattern.ask(crmService, CrmActor.GetGroups) map {
                case list: Seq[Any] => HttpResponse(StatusCodes.OK, entity = write[Seq[Crm.Group]](list.asInstanceOf[Seq[Crm.Group]]))
              } recover {
                case _ => HttpResponse(StatusCodes.BadRequest)
              })
            }
          }
        } ~ pathPrefix("user") {
          post {
            extract(_.request) {
              req => complete( Unmarshal(req.entity).to[String] map read[CrmActor.UserAdd] flatMap{
                pattern.ask(crmService, _) map {
                  case user: Crm.User => HttpResponse(StatusCodes.Created, entity = write[Crm.User](user))
                }
              } recover{
                case _ => HttpResponse(StatusCodes.BadRequest)
              }
              )
            }
          } ~ get {
            complete(pattern.ask(crmService, CrmActor.GetUsers) map {
              case list:Seq[Any] => HttpResponse(StatusCodes.OK, entity = write[Seq[Crm.User]](list.asInstanceOf[Seq[Crm.User]]))
            } recover {
              case _ => HttpResponse(StatusCodes.BadRequest)
            })
          }
        }
      } ~ path("chat"){
        extract(_.request) {
          req => complete(wsHandler(req))
        }
      }
    }


  val port = 9100
  val binding = Http().bindAndHandle(interface = "0.0.0.0", port=port, handler = apiRoute)
  println(s"Listening for connections on port $port...")

  StdIn.readLine()
  binding.flatMap(_.unbind()).onComplete(_=>system.terminate())

}
