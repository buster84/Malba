package jp.co.shanon.malba.worker

import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Stash
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.Terminated
import akka.contrib.pattern.ClusterClient.SendToAll
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy.Stop
import akka.actor.SupervisorStrategy.Restart
import akka.actor.ActorInitializationException
import akka.actor.DeathPactException
import scala.util.Random
import akka.actor.ActorIdentity
import akka.actor.Identify
import org.joda.time.DateTime

object Worker {

  def props(actorPath:String, taskType: String, master: ActorRef): Props =
    Props(classOf[Worker], actorPath, taskType, master)

  case class WorkComplete(taskId: String)
}

class Worker(actorPath: String, taskType: String, master: ActorRef) extends Actor with Stash with ActorLogging {
  val identifyId = Random.nextInt

  override def supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: DeathPactException           => Stop
    case _: Exception                    => Stop
  }

  override def preStart() = {
    context.actorSelection(actorPath) ! Identify(identifyId)
  }

  var taskExecuter : ActorRef = _

  def receive = connecting

  def connecting: Receive = {
    case ActorIdentity(`identifyId`, Some(ref)) =>
      unstashAll()
      taskExecuter = ref
      context.watch(ref)
      context.become(connected)
      self ! WorkerProtocol.TaskIsReady // Try to get a task after connecting
    case ActorIdentity(`identifyId`, None) => context.stop(self)
    case message => stash()
  }

  def connected: Receive = {
    case WorkerProtocol.TaskIsReady =>
      taskExecuter ! WorkerProtocol.TaskIsReady

    case message : MalbaProtocol.GetTaskRequest =>
      master forward message

    case WorkerProtocol.GetState =>
      taskExecuter forward WorkerProtocol.GetState
  }

  override def unhandled(message: Any): Unit = message match {
    case Terminated(ref) if ref == taskExecuter => context.stop(self)
    case WorkerProtocol.TaskIsReady => 
    case _                          => super.unhandled(message)
  }
}
