package jp.co.shanon.malba.worker

import scala.collection.immutable.Queue
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._
import akka.pattern._
import scala.concurrent.duration.Deadline
import scala.concurrent.duration.FiniteDuration
import akka.actor.Props
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence.PersistentActor
import akka.persistence.AtLeastOnceDelivery
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Master {

  def props(workManagerId: String, snapshotInterval: FiniteDuration): Props =
    Props(classOf[Master], workManagerId, snapshotInterval)
}

class Master(workManagerId: String, snapshotInterval: FiniteDuration) extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = Duration(1, MINUTES)) {
    case e: Throwable => 
      log.error(e, s"Unknow error happen. sender: ${sender().toString} message: ${e.getMessage}")
      // Shutdown cause to finish JVM process and start singleton master in another node, 
      //   so you should use monit or something to monitor the process for restarting Malba after finishing the process.
      context.system.shutdown()
      Stop
  }

  val workerManager = context.actorOf(Props(classOf[WorkerManager], workManagerId, self), "workerManager")
  val queueManager  = context.actorOf(QueueManager.props(snapshotInterval))

  def receive = {
    case message : MasterProtocol.Notify =>
      workerManager forward message

    case message @ MalbaProtocol.AddWorkerRequest( id, taskType, actorPath ) =>
      workerManager forward message

    case message @ MalbaProtocol.GetWorkerStateRequest(_) =>
      workerManager forward message

    case message =>
      queueManager forward message
  }
}
