package jp.co.shanon.malba.client

import java.util.UUID
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
import akka.actor.Stash
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import org.joda.time.DateTime
import scala.concurrent._
import scala.concurrent.duration._
import jp.co.shanon.malba.worker.WorkerProtocol.TaskIsReady
import jp.co.shanon.malba.worker.WorkerProtocol.GetState
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

  def setStopping(workManager: ActorRef): Unit = {
    workManager ! MalbaWorkProtocol.Closing
  }

  def awaitTermination(workManager: ActorRef): Unit = {
    setStopping(workManager)
    while(isWorking(workManager)){
      setStopping(workManager)
      Thread.sleep(30000)
    }
  }

  def awaitTermination(workManager: ActorRef, timeout: FiniteDuration): Unit = {
    setStopping(workManager)
    val deadline = Deadline.now + timeout
    while(isWorking(workManager) && !deadline.isOverdue){
      setStopping(workManager)
      Thread.sleep(30000)
    }
  }

  def isWorking(workManager: ActorRef): Boolean = {
    implicit val timeout = Timeout(Duration(60, SECONDS))
    Await.result((workManager ask MalbaWorkProtocol.IsWorking).mapTo[Boolean], Duration(60, SECONDS))
  }
}

class MalbaWorkManager(
  malbaClient: MalbaClient, 
  taskType: String,
  workExecutorProps: Props,
  pollingInterval: FiniteDuration,
  workTimeout: Duration,
  maxRetryCount: Int
) extends Actor with Stash with ActorLogging {
  import context.dispatcher
  override def supervisorStrategy = OneForOneStrategy() {
    case e: Exception => 
      log.error(s"Unknow error happen. message: ${e.getMessage} ${e.getStackTrace}")
      receiverWithConnecting(idle)
      Stop
  }

  private case object Connect

  val taskPolling    = context.system.scheduler.schedule(pollingInterval, pollingInterval, self, TaskIsReady)
  val connectingTick = context.system.scheduler.schedule(Duration(0, SECONDS), Duration(30, SECONDS), self, Connect)

  var isStopping: Boolean = false

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
    case GetState => sender() ! MalbaProtocol.Idle(self)
    case MalbaWorkProtocol.IsWorking => sender() ! false
    case TaskIsReady => 
      if(!isStopping){
        malbaClient.getTask(taskType) pipeTo self
        receiverWithConnecting(waitForTask)
      }
  }

  def waitForTask: Receive = {
    case GetState     => stash()
    case MalbaWorkProtocol.IsWorking => sender() ! true
    case MalbaProtocol.GetTaskResponse( id, from, taskType, status, task ) =>
      val worker = context.watch(context.actorOf(workExecutorProps))
      context.setReceiveTimeout(workTimeout)
      receiverWithConnecting(working(worker, task))
      worker ! task
      unstashAll()

    case MalbaProtocol.GetNoTaskResponse( id, from, taskType ) =>
      log.info(s"No task. taskType=${taskType} id=${id}")
      receiverWithConnecting(idle)
      unstashAll()

    case akka.actor.Status.Failure(e) =>
      log.error(s"Failed to get task. ${e.getMessage} ${e.getStackTrace()}")
      receiverWithConnecting(idle)
      unstashAll()
  }

  def working(worker: ActorRef, task: Task, startDate: DateTime = DateTime.now(), tryCount: Int = 1): Receive = {
    case MalbaWorkProtocol.Closing => 
      isStopping = true
      worker ! MalbaWorkProtocol.Closing

    case GetState     =>
      sender() ! MalbaProtocol.Busy(self, task, startDate, tryCount)

    case MalbaWorkProtocol.IsWorking => sender() ! true

    case MalbaWorkProtocol.WorkComplete =>
      context.setReceiveTimeout(Duration.Undefined)
      context.unwatch(worker)
      context.stop(worker)
      val duration = new org.joda.time.Duration(startDate, DateTime.now())
      log.info(s"Finished task taskType=${taskType} id=${task.id} complete time=${duration.getStandardSeconds().toString}")
      receiverWithConnecting(idle)
      
      self ! TaskIsReady

    case ReceiveTimeout if maxRetryCount > tryCount =>
      log.warning(s"Receive timeout. Try count is ${tryCount.toString} taskType=${taskType}. Trying again.")
      context.unwatch(worker)
      context.stop(worker)
      val newWorker = context.watch(context.actorOf(workExecutorProps))
      receiverWithConnecting(working(newWorker, task, startDate, tryCount + 1))
      newWorker ! task

    case ReceiveTimeout =>
      log.error(s"Receive timeout. Try count is ${tryCount.toString} taskType=${taskType}")
      context.unwatch(worker)
      context.stop(worker)
      context.setReceiveTimeout(Duration.Undefined)
      receiverWithConnecting(idle)

    case Terminated(`worker`) => 
      log.error(s"Terminated worker[${worker.toString}]. Try count is ${tryCount.toString} taskType=${taskType}.")
      context.unwatch(worker)

      if(maxRetryCount > tryCount) {
        log.info(s"Try again taskType=${taskType}")
        val newWorker = context.watch(context.actorOf(workExecutorProps))
        receiverWithConnecting(working(newWorker, task, startDate, tryCount + 1))
        newWorker ! task
      } else {
        log.info(s"Become idle taskType=${taskType}")
        receiverWithConnecting(idle)
        context.setReceiveTimeout(Duration.Undefined)
      }
  }

  override def unhandled(message: Any): Unit = message match {
    case MalbaWorkProtocol.Closing   => isStopping = true
    case TaskIsReady                 =>
    case _                           => super.unhandled(message)
  }
}
