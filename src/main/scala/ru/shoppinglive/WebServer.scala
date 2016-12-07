package ru.shoppinglive

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, UpgradeToWebSocket}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.io.StdIn

/**
  * Created by rkhabibullin on 06.12.2016.
  */
object WebServer extends App{
  final case class ContactResponse(login: String, id: Int, io:String)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val mapping : HttpRequest=>HttpResponse = {
    case req @ HttpRequest(HttpMethods.GET, Uri.Path("/chat"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) =>
          println("got connection "+req.headers)
          val connActor = system.actorOf(ClientConnection.props)
          upgrade.handleMessagesWithSinkSource(Sink.foreach{
            case tm: TextMessage =>
            case bm: BinaryMessage => bm.dataStream.runWith(Sink.ignore)
          }, Source.)
        case None => HttpResponse(StatusCodes.BadRequest)
      }
    case r: HttpRequest =>
      r.discardEntityBytes()
      HttpResponse(StatusCodes.BadRequest)
  }

  system.actorOf()

  val binding = Http().bindAndHandleSync(interface = "0.0.0.0", port=9100, handler = mapping)
  println(s"Listening for connections...")

  StdIn.readLine()
  binding.flatMap(_.unbind()).onComplete(_=>system.terminate())

}
