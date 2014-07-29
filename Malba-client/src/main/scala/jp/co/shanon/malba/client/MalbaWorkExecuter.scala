package jp.co.shanon.malba.client

import java.util.UUID
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.Terminated
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy.Stop
import akka.actor.SupervisorStrategy.Restart
import akka.actor.ActorInitializationException
import akka.actor.DeathPactException
import akka.pattern.pipe
import org.joda.time.DateTime
import jp.co.shanon.malba.worker.WorkerProtocol.TaskIsReady
import jp.co.shanon.malba.worker.MalbaProtocol
import jp.co.shanon.malba.worker.Task

object MalbaWorkManager {

  def props( 
    malbaClient: MalbaClient, 
    taskType: String, 
    workExecutorProps: Props, 
    pollingInterval: FiniteDuration, 
    workTimeout: Duration, 
    maxRetryCount: Int ): Props = {
    Props(classOf[MalbaWorkManager], malbaClient, taskType, workExecutorProps, pollingInterval, workTimeout, maxRetryCount)
  }
}

class MalbaWorkManager(
  malbaClient: MalbaClient, 
  taskType: String,
  workExecutorProps: Props,
  pollingInterval: FiniteDuration,
  workTimeout: Duration,
  maxRetryCount: Int
) extends Actor with ActorLogging {
  import context.dispatcher
  override def supervisorStrategy = OneForOneStrategy() {
    case _: Exception => 
      receiverWithConnecting(idle)
      Stop
  }

  private case object Connect

  val taskPolling    = context.system.scheduler.schedule(pollingInterval, pollingInterval, self, TaskIsReady)
  val connectingTick = context.system.scheduler.schedule(Duration(0, SECONDS), Duration(30, SECONDS), self, Connect)


  override def postStop(): Unit = {
    taskPolling.cancel()
    if(!connectingTick.isCancelled) 
      connectingTick.cancel()
    ()
  }

  def receiverWithConnecting(receiver: Receive): Unit = {
    context.become(receiver orElse connecting)
  }

  def receive = connecting orElse idle

  def connecting: Receive = {
    case Connect =>
      log.info(s"Try to connect to Malba. worker=${self.toString} taskType=${taskType}")
      malbaClient.addWorker(taskType, self) pipeTo(self)
      ()

    case MalbaProtocol.Ok =>
      log.info(s"Added worker. worker=${self.toString} taskType=${taskType}")
      connectingTick.cancel()
      ()

    case MalbaProtocol.Reject("400", message) =>
      log.warning(s"Failed to add worker. message=${message}. Give up to add worker.")
      connectingTick.cancel()
      ()

    case MalbaProtocol.IntenalServerError => 
      log.warning(s"Failed to add worker because of internal server. Try to connect again.")

    case akka.actor.Status.Failure(e) =>
      log.warning(s"Failed to add worker because of ${e.getMessage}. Try to connect again.")
  }

  def idle: Receive = {
    case TaskIsReady => 
      malbaClient.getTask(taskType) pipeTo self
      receiverWithConnecting(waitForTask)
  }

  def waitForTask: Receive = {
    case MalbaProtocol.GetTaskResponse( id, from, taskType, status, task ) =>
      val worker = context.watch(context.actorOf(workExecutorProps))
      context.setReceiveTimeout(workTimeout)
      receiverWithConnecting(working(worker, task))
      worker ! task

    case MalbaProtocol.GetNoTaskResponse( id, from, taskType ) =>
      log.info(s"No task. taskType=${taskType} id=${id}")
      receiverWithConnecting(idle)

    case akka.actor.Status.Failure(e) =>
      log.error(s"Failed to get task. ${e.getMessage} ${e.getStackTrace()}")
      receiverWithConnecting(idle)
  }

  def working(worker: ActorRef, task: Task, startDate: DateTime = DateTime.now(), tryCount: Int = 1): Receive = {
    case MalbaWorkProtocol.WorkComplete =>
      context.setReceiveTimeout(Duration.Undefined)
      val duration = new org.joda.time.Duration(startDate, DateTime.now())
      log.info(s"Finished task taskType=${taskType} id=${task.id} complete time=${duration.getStandardSeconds().toString}")
      receiverWithConnecting(idle)
    case ReceiveTimeout if maxRetryCount > tryCount =>
      log.warning(s"Receive timeout. Try count is ${tryCount.toString} taskType=${taskType}. Trying again.")
      context.unwatch(worker)
      context.stop(worker)
      val newWorker = context.watch(context.actorOf(workExecutorProps))
      receiverWithConnecting(working(newWorker, task, startDate, tryCount + 1))
    case ReceiveTimeout =>
      log.error(s"Receive timeout. Try count is ${tryCount.toString} taskType=${taskType}")
      context.unwatch(worker)
      context.stop(worker)
      receiverWithConnecting(idle)
  }

  override def unhandled(message: Any): Unit = message match {
    case TaskIsReady                =>
    case _                          => super.unhandled(message)
  }

}
