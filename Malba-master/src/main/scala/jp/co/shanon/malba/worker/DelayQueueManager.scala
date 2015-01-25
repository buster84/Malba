package jp.co.shanon.malba.worker

import scala.collection.immutable.Queue
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.pattern._
import scala.concurrent.duration.Deadline
import scala.concurrent.duration.FiniteDuration
import akka.actor.Props
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.cluster.Cluster
import akka.persistence.PersistentActor
import akka.persistence.AtLeastOnceDelivery
import akka.persistence.RecoveryFailure
import akka.persistence.PersistenceFailure
import akka.persistence.SaveSnapshotFailure
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.SnapshotOffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.annotation.tailrec
import org.joda.time.DateTime

object DelayQueueManager {

  def props(scheduleInterval:FiniteDuration, snapshotInterval: FiniteDuration): Props =
    Props(classOf[DelayQueueManager], scheduleInterval, snapshotInterval)

  case object TakeSnapshotTick
}

class DelayQueueManager(scheduleInterval:FiniteDuration, snapshotInterval: FiniteDuration) extends PersistentActor with ActorLogging {
  import DelayQueueManager._
  private case object CleanupTick
  private case object CheckScheduleTick

  val deleteAfter: FiniteDuration = Duration(30, SECONDS)
  def getDeadline = {
    Deadline.now + deleteAfter
  }

  var commandIdForAddTask: Map[String, (MalbaProtocol.AddTaskResponse, Deadline)]       = Map.empty[String, (MalbaProtocol.AddTaskResponse, Deadline)]
  var commandIdForCancelTask: Map[String, (MalbaProtocol.CancelTaskResponse, Deadline)] = Map.empty[String, (MalbaProtocol.CancelTaskResponse, Deadline)]

  val cleanupTask          = context.system.scheduler.schedule(deleteAfter, deleteAfter, self, CleanupTick)

  val takeSnapshotSchedule = context.system.scheduler.schedule(snapshotInterval, snapshotInterval, self, TakeSnapshotTick)

  val checkScheduleTask    = context.system.scheduler.schedule(Duration( 0, SECONDS ), scheduleInterval, self, CheckScheduleTick)

  override def postStop(): Unit = {
    cleanupTask.cancel()
    takeSnapshotSchedule.cancel()
    checkScheduleTask.cancel()
    ()
  }


  // persistenceId must include cluster role to support multiple queueManager 
  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => role + "-delayQueueManager"
    case None       => "delayQueueManager"
  }

  var delayQueueManagerState: DelayQueueManagerState = DelayQueueManagerState.empty

  override def receiveRecover = {
    case event: DelayQueueManagerState.DelayQueueManagerDomainEvent =>
      delayQueueManagerState = delayQueueManagerState.updated(event)

    case SnapshotOffer(metadata, state: DelayQueueManagerState) =>
      log.info(s"Recover from snapshot (metadata = ${metadata.toString})")
      delayQueueManagerState = state
    case RecoveryFailure(cause) =>
      log.error(cause, s"Failed to recover (persisten id = [${persistenceId}])")
      throw new Exception(s"Failed to recover (persisten id = [${persistenceId}])")
  }

  override def receiveCommand = {
    case request @ MalbaProtocol.AddDelayTaskRequest ( id, taskId, from, group, option, taskType, task, scheduledTime ) =>
      println( request.toString )
      if(commandIdForAddTask.isDefinedAt(id)){
        val response: MalbaProtocol.AddTaskResponse = commandIdForAddTask.apply( id )._1
        sender() ! response
      } else {
        val event    = DelayQueueManagerState.DelayTaskAdded(request)
        val response = MalbaProtocol.AddTaskResponse ( id, from, taskId,  group, taskType, MalbaProtocol.Ok, 0L, 0L, 0L ) // TODO: Implement seq number
        commandIdForAddTask = commandIdForAddTask + (id -> Tuple2(response, getDeadline))
        persist(event) { evt =>
          delayQueueManagerState = delayQueueManagerState.updated( evt )
          sender() ! response
        }
      }

    case MalbaProtocol.CancelDelayTaskByIdRequest ( id, from, taskType, taskId ) =>
      if(commandIdForCancelTask.isDefinedAt(id)){
        val response: MalbaProtocol.CancelTaskResponse = commandIdForCancelTask.apply( id )._1
        sender() ! response
      } else {
        if(!delayQueueManagerState.contains(taskType, taskId)){
          val response = MalbaProtocol.CancelTaskByIdResponse(id, from, taskType, taskId, MalbaProtocol.Reject("404", "Not found task id"))
          commandIdForCancelTask = commandIdForCancelTask + (id -> Tuple2(response, getDeadline))
          sender() ! response
        } else {
          val event    = DelayQueueManagerState.DelayTaskCanceledById(id, from, taskType, taskId)
          val response = MalbaProtocol.CancelTaskByIdResponse ( id, from, taskType, taskId, MalbaProtocol.Ok )
          commandIdForCancelTask = commandIdForCancelTask + (id -> Tuple2(response, getDeadline))
          persist(event) { evt =>
            delayQueueManagerState = delayQueueManagerState.updated( evt )
            sender() ! response
          }
        }
      }

    case MalbaProtocol.CancelDelayTaskByGroupRequest ( id, from, taskType, group ) =>
      if(commandIdForCancelTask.isDefinedAt(id)){
        val response: MalbaProtocol.CancelTaskResponse = commandIdForCancelTask.apply( id )._1
        sender() ! response
      } else {
        val event    = DelayQueueManagerState.DelayTaskCanceledByGroup(id, from, taskType, group)
        val response = MalbaProtocol.CancelTaskByGroupResponse ( id, from, taskType, group, MalbaProtocol.Ok )
        commandIdForCancelTask = commandIdForCancelTask + (id -> Tuple2(response, getDeadline))
        persist(event) { evt =>
          delayQueueManagerState = delayQueueManagerState.updated( evt )
          sender() ! response
        }
      }

    case CheckScheduleTick =>
      if( delayQueueManagerState.existsEqueueableTask() ){
        persist(DelayQueueManagerState.DelayTasksSent( DateTime.now )) { evt =>
          val ( requestList, newState ) = delayQueueManagerState.takeAllEnqueueableTasks(evt.now)
          delayQueueManagerState = newState

          requestList.foreach{ request =>
            log.info( s"Attemp to send request[${request.toString}]" )
            context.parent ! request.toAddTaskRequest
          }
        }
      } else {
        ()
      }

    case MalbaProtocol.AddTaskResponse(id, taskId, from, group, taskType, status, _, _, _) =>
      if( status.code.startsWith("2") ){
        log.info( s"Succeeded to enqueue id=${id} taskId=${taskId} from=${from} group=${group.toString} taskType=${taskType}" )
      } else {
        status match {
          case MalbaProtocol.Reject( code, message ) =>
            log.warning( s"Failed to enqueue code=${code} message=${message} id=${id} taskId=${taskId} from=${from} group=${group.toString} taskType=${taskType}" )
          case _ =>
            log.error( s"Failed to enqueue. Unknown status=${status.toString} id=${id} taskId=${taskId} from=${from} group=${group.toString} taskType=${taskType}" )
        }
      }

    case CleanupTick =>
      commandIdForAddTask = commandIdForAddTask.filterNot {
        case (_, (_, deadline)) => deadline.isOverdue
      }
      commandIdForCancelTask = commandIdForCancelTask.filterNot {
        case (_, (_, deadline)) => deadline.isOverdue
      }


    case TakeSnapshotTick =>
      saveSnapshot(delayQueueManagerState)

    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Succeed to take snapshot (persistent id = [${persistenceId}], metadata = [${metadata.toString}])")
      val sequenceNr = metadata.sequenceNr - 1
      log.info(s"Try to delete messages with sequence numbers less than or equal to ${sequenceNr.toString}")
      deleteMessages(sequenceNr)

    case SaveSnapshotFailure(metadata, cause) =>
      val errorMsg = "Failed to take snapshot" +
        s"(persistent id = [${persistenceId}], metadata = [${metadata.toString}]). " + cause
      log.error(cause, errorMsg)
      throw new Exception(errorMsg)

    case PersistenceFailure(payload, sequenceNumber, cause) =>
      val errorMsg = "Failed to store data into journal" +
        s"(persistent id = [${persistenceId}], sequence nr = [${sequenceNumber}], payload class = [${payload.getClass.getName}]). " + cause
      log.error(cause, errorMsg)
      throw new Exception(errorMsg)
  }
}
