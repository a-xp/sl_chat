package ru.shoppinglive.chat.client_connection

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.event.LoggingReceive
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.json4s.native.Serialization.read
import ru.shoppinglive.ChatService
import ru.shoppinglive.ClientConnection.ClientAuthenticated

import scala.util.{Failure, Success}

/**
  * Created by rkhabibullin on 13.12.2016.
  */
class Connection extends Actor with ActorLogging {

  var clientId = 0
  var out:Option[ActorRef] = None
  var in:Option[ActorRef] = None
  import ru.shoppinglive.ChatService._
  implicit val materializer = ActorMaterializer()
  implicit val ec = context.dispatcher

  override def receive:Receive = LoggingReceive {
    case NewConnection =>
      sender ! (Sink.actorSubscriber[Message](Props(new RecieverActor(self))), Source.actorPublisher[Message](Props(new SenderActor(self))))
    case TokenCmd(token) =>
      http.singleRequest(HttpRequest(uri = "http://rkhabibullin.old.shoppinglive.ru/crm/modules/ajax/authorization/token/info?token=" + token)) flatMap {
        case HttpResponse(StatusCodes.OK, _, entity,_) =>
          Unmarshal(entity).to[String]
      } map {
        import org.json4s.native.Serialization.read
        import org.json4s.DefaultFormats
        implicit val formats = DefaultFormats + StringToInt
        read[TokenInfo]
      } onComplete{
        case Success(ti) =>
          out.get ! AuthResult(true)
          self ! ClientAuthenticated(ti.id)
          context.parent ! ConnectedCmd(ti.id)
        case Failure(t) => out.get ! AuthResult(false)
      }
    case ClientAuthenticated(id) => clientId = id
    case "output rdy" => out = Some(sender); context.watch(sender)
    case "input rdy" => in = Some(sender); context.watch(sender)
    case result:ChatService.Result => out foreach(_ forward result)
    case cmd: ChatService.Cmd => context.parent ! cmd
    case Terminated => context.parent ! DisconnectedCmd
      self ! PoisonPill
  }
}

object Connection {
  def props = Props[Connection]

  case class SenderRdy()
  case class RecieverRdy()
}


