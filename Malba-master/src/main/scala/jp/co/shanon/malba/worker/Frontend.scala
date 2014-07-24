package jp.co.shanon.malba.worker

import scala.concurrent.duration._
import akka.actor.Actor
import akka.pattern._
import akka.util.Timeout
import akka.contrib.pattern.ClusterSingletonProxy

class Frontend extends Actor {
  import MalbaProtocol._
  import context.dispatcher
  val masterProxy = context.actorOf(ClusterSingletonProxy.props(
    singletonPath = "/user/master/active",
    role = Some("backend")),
    name = "masterProxy")

  def receive = {
    case AddMeAsWorkerRequest ( id, taskType ) => 
      val request = AddWorkerRequest ( id, taskType, sender().path.toString )
      self forward request

    case message =>
      implicit val timeout = Timeout(5.seconds)
      ((masterProxy ? message) recover { 
        case e: Exception => IntenalServerError(e.getMessage)
      }) pipeTo sender()
      ()
  }
}
