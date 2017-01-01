package ru.shoppinglive.chat.server

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, UpgradeToWebSocket}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.mongodb.casbah.Imports.MongoClient
import ru.shoppinglive.chat.admin_api.CrmActor
import ru.shoppinglive.chat.client_connection.ConnectionSupervisor
import ru.shoppinglive.chat.domain.Crm
import ru.shoppinglive.chat.domain.Crm.RoleSerializer
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by rkhabibullin on 20.12.2016.
  */
class Router(implicit inj:Injector) extends Injectable{

  import akka.http.scaladsl.server.Directives._
  import ch.megard.akka.http.cors.CorsDirectives._
  import org.json4s.native.Serialization.{read, write}

  import scala.concurrent.duration._
  implicit private val formats = org.json4s.DefaultFormats + RoleSerializer
  implicit private val timeout = Timeout(5.second)
  implicit private val ec = inject [ExecutionContext]
  implicit private val mat = inject [Materializer]

  def handler = cors() {
    pathPrefix("admin") {
      pathPrefix("group") {
        path(IntNumber) {
          id => get {
            complete(pattern.ask(inject [ActorRef] ('crm), CrmActor.GetGroup(id)).map {
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
                  pattern.ask(inject [ActorRef] ('crm), _) map {
                    case grp:Crm.Group => HttpResponse(StatusCodes.OK, entity = write[Crm.Group](grp))
                  }
                } recover{
                  case _ => HttpResponse(StatusCodes.BadRequest)
                })
            }
          } ~ get {
            complete(pattern.ask(inject [ActorRef] ('crm), CrmActor.GetGroups) map {
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
              pattern.ask(inject [ActorRef] ('crm), _) map {
                case user: Crm.User => HttpResponse(StatusCodes.Created, entity = write[Crm.User](user))
              }
            } recover{
              case _ => HttpResponse(StatusCodes.BadRequest)
            }
            )
          }
        } ~ get {
          complete(pattern.ask(inject [ActorRef] ('crm), CrmActor.GetUsers) map {
            case list:Seq[Any] => HttpResponse(StatusCodes.OK, entity = write[Seq[Crm.User]](list.asInstanceOf[Seq[Crm.User]]))
          } recover {
            case _ => HttpResponse(StatusCodes.BadRequest)
          })
        }
      } ~ path("reset") {
        post {
          complete{
              clearDb()
              inject[ActorRef]('crm) ! "reset"
              inject[ActorRef]('contacts) ! "reset"
              inject[ActorRef]('dialogs) ! "reset"
              HttpResponse(StatusCodes.OK)
          }
        } ~ {
          complete(HttpResponse(StatusCodes.AlreadyReported))
        }
      }
     } ~ path("chat"){
      extract(_.request) {
        req => complete(wsHandler(req))
      }
    }
  }

  def wsHandler: HttpRequest => Future[HttpResponse] = {
    case req @ HttpRequest(HttpMethods.GET, Uri.Path("/chat"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) =>
          import scala.concurrent.duration._
          pattern.ask(inject [ActorRef]('connections), ConnectionSupervisor.CreateConnection)(1.seconds) map {
            case (sink, source) =>
              upgrade.handleMessagesWithSinkSource(sink.asInstanceOf[Sink[Message, akka.actor.ActorRef]],
                source.asInstanceOf[Source[Message, akka.actor.ActorRef]])
            case _ =>
              HttpResponse(StatusCodes.InternalServerError)
          }
        case None => Future.successful(HttpResponse(StatusCodes.BadRequest))
      }
    case r: HttpRequest =>
      r.discardEntityBytes()
      Future.successful(HttpResponse(StatusCodes.BadRequest))
  }

  private def clearDb():Unit = {
    import com.mongodb.casbah.Imports._
    val jUri = MongoClientURI(inject [String]("casbah-journal.mongo-url"))
    val jConn = MongoClient(jUri)
    val jDb = jConn(jUri.database.get)
    jDb.dropDatabase()
    val sUri = MongoClientURI(inject [String]("casbah-snapshot.mongo-url"))
    val sConn = MongoClient(sUri)
    val sDb = sConn(sUri.database.get)
    sDb.dropDatabase()
  }


}
