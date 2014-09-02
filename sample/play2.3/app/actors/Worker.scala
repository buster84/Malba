package actors

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.pipe
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.joda.time.DateTime
import java.net.URL
import jp.co.shanon.malba.worker.Task
import jp.co.shanon.malba.client.MalbaWorkProtocol.WorkComplete
import jp.co.shanon.malba.client.MalbaWorkProtocol.Closing

object Worker {
  def props() = {
    Props(classOf[Worker])
  }
}

class Worker extends Actor with ActorLogging {
  def receive = {
    case Task( id, "test", task) =>
      log.info(s"Receive task id=${id}")
      context.parent ! WorkComplete
    case Closing   => 
      log.info(s"Closing")
      context.parent ! WorkComplete
      
    case akka.actor.Status.Failure(e) =>
      log.error(e, s"Something wrong. ${e.getMessage}")
      context.parent ! WorkComplete
  }
}
