package jp.co.shanon.malba.worker

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Terminated
import akka.pattern._
import scala.concurrent.duration.Deadline
import scala.concurrent.duration.FiniteDuration
import akka.actor.Props
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.cluster.Cluster
import akka.persistence.PersistentActor
import akka.persistence.AtLeastOnceDelivery
import akka.persistence.RecoveryCompleted
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class WorkerManager(id: String, master: ActorRef) extends PersistentActor with AtLeastOnceDelivery  with ActorLogging {
  type TaskType     = String
  type ActorPathStr = String

  private case object CleanupTick

  val deleteAfter: FiniteDuration = Duration(30, SECONDS)


  var workerState                               = WorkerState.empty
  var workerRefs: Map[TaskType, Seq[(ActorPathStr, ActorRef)]] = Map.empty[TaskType, Seq[(ActorPathStr, ActorRef)]]
  var commandIds: Map[String, Deadline]         = Map.empty[String, Deadline]

  val cleanupTask = context.system.scheduler.schedule(deleteAfter, deleteAfter, self, CleanupTick)
  override def postStop(): Unit = {
    cleanupTask.cancel()
    ()
  }


  override def persistenceId: String = id

  override def receiveRecover = {
    case evt: WorkerState.WorkerDomainEvent  => workerState = workerState.updated( evt )
    case RecoveryCompleted => 
      workerState.workerMap.foreach {
        case ( taskType, actorPathList ) =>
          actorPathList.foreach { actorPath => addWorker( actorPath, taskType ) }
      }
  }

  def addWorker(actorPath: String, taskType:String): Unit = {
    val worker  = context.watch(context.actorOf(Worker.props(actorPath, taskType, master)))
    workerRefs  = workerRefs + (taskType -> (workerRefs.getOrElse(taskType, Seq.empty[(ActorPathStr, ActorRef)]) ++ Seq((actorPath, worker))))
  }

  def killWorker(actorPath: String, taskType: String): Unit = {
    val (worker, rest) = workerRefs.getOrElse(taskType, Seq.empty[(ActorPathStr, ActorRef)]).partition{
      case (path, ref) if path == actorPath => true
      case _ => false
    }
    worker.foreach(pair => context.unwatch(pair._2))

    if(!rest.isEmpty){
      workerRefs = workerRefs + (taskType -> rest)
    } else {
      workerRefs = workerRefs.filterKeys(key => key != taskType)
    }
  }

  override def receiveCommand = {
    case MalbaProtocol.AddWorkerRequest( id, taskType, actorPath ) =>
      if(commandIds.isDefinedAt(id)){
        sender() ! MalbaProtocol.AddWorkerResponse ( id, taskType, actorPath, MalbaProtocol.Ok )
      } else {
        if(workerState.contains(actorPath)){
          sender() ! MalbaProtocol.AddWorkerResponse ( id, taskType, actorPath, MalbaProtocol.Reject("400", s"There is already ${actorPath} worker."))
        } else {
          commandIds = commandIds + (id -> (Deadline.now + deleteAfter))

          val event  = WorkerState.WorkerAdded(actorPath, taskType)
          persist(event) { evt =>
            workerState = workerState.updated(evt)
            addWorker( evt.actorPath, evt.taskType )
            sender() ! MalbaProtocol.AddWorkerResponse ( id, taskType, actorPath, MalbaProtocol.Ok )
          }
        }
      }

    case MasterProtocol.Notify( taskType ) => 
      workerRefs.foreach {
        case ( `taskType`, workers ) => workers.foreach(worker => worker._2 ! WorkerProtocol.TaskIsReady) 
        case _ => ()
      }
    case MalbaProtocol.GetWorkerStateRequest( taskType ) =>
      implicit val timeout = akka.util.Timeout(5.seconds)
      workerRefs.get(taskType).map{ workers =>
        Future.sequence(workers.map { worker => 
          (worker._2 ? WorkerProtocol.GetState).mapTo[MalbaProtocol.State] recover {
            case e: Exception => 
              log.error(e, s"Couldn't get worker state. message: ${e.getMessage}")
              MalbaProtocol.Unknown(worker._1)
          }
        }).map(stateList => MalbaProtocol.GetWorkerStateResponse( taskType, stateList ))
      }.getOrElse {
        Future.successful(MalbaProtocol.GetWorkerStateResponse( taskType, Seq.empty[MalbaProtocol.State]))
      } pipeTo sender()
      ()

    case CleanupTick =>
      commandIds = commandIds.filterNot{
        case (actorPath, deadline) => deadline.isOverdue
      }

    case Terminated(worker) => 
      workerRefs.foreach{
        case (taskType, workers) =>
          workers.foreach{ 
            case (actorPath, actor) if(actor == worker) => 
              val event = WorkerState.WorkerKilled(actorPath, taskType)
              persist(event) { evt =>
                workerState = workerState.updated(evt)
                killWorker(evt.actorPath, evt.taskType)
              }
            case _ => () // Nothing to do
          }
      }
  }
}
