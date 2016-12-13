package ru.shoppinglive

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.server.Route
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, UpgradeToWebSocket}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.ConfigFactory

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
  val contactSupervisor = system.actorOf(ChatService.props, "contacts")
  val connectionSupervisor = system.actorOf(ClientConnection.props, "connections")
  val crmService = system.actorOf(CrmService.props, "crm")

  import akka.http.scaladsl.server.Directives._

  val wsHandler : HttpRequest=>Future[HttpResponse] = {
    case req @ HttpRequest(HttpMethods.GET, Uri.Path("/chat"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) =>
          import scala.concurrent.duration._
          pattern.ask(connectionSupervisor, ClientConnection.NewConnection)(1 seconds) map {
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

  val apiRoute =
    pathPrefix("rest"){
      import org.json4s.native.Serialization.{write,read}
      import scala.concurrent.duration._
      implicit val formats = org.json4s.DefaultFormats
      path("group" / IntNumber) {
        id => get {
          complete(pattern.ask(crmService, CrmService.GetGroup)(50 milliseconds).map {
            case (CrmService.GetGroup, Some(grp:CrmService.Group))
              => HttpResponse(status =StatusCodes.OK, entity = write[CrmService.Group](grp))
          } recover {
            case _ => HttpResponse(StatusCodes.NotFound)
          })
        } ~ put {
          complete("update group"+id)
        }
      }~(path("group") & post){
        extract(_.request){
          req =>
            complete(Unmarshal(req.entity).to[String] map read[CrmService.Group] flatMap {
              case CrmService.Group(id, name) => pattern.ask(crmService, true)(100 milliseconds) map {
                case true => HttpResponse(StatusCodes.OK)
                case _ => HttpResponse(StatusCodes.BadRequest)
              }
            } recover{
              case _ => HttpResponse(StatusCodes.BadRequest)
            })
        }
      }
    } ~ path("chat"){
      extract(_.request) {
        req => complete(wsHandler(req))
      }
    }

  val port = 9100
  val binding = Http().bindAndHandle(interface = "0.0.0.0", port=port, handler = apiRoute)
  println(s"Listening for connections on port $port...")

  StdIn.readLine()
  binding.flatMap(_.unbind()).onComplete(_=>system.terminate())

}
