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

object QueueManager {

  def props(snapshotInterval: FiniteDuration): Props =
    Props(classOf[QueueManager], snapshotInterval)

  case object TakeSnapshotTick
}

class QueueManager(snapshotInterval: FiniteDuration) extends PersistentActor with ActorLogging {
  import QueueManager._
  private case object CleanupTick
  val deleteAfter: FiniteDuration = Duration(30, SECONDS)
  def getDeadline = {
    Deadline.now + deleteAfter
  }

  var commandIdForAddTask: Map[String, (MalbaProtocol.AddTaskResponse, Deadline)]       = Map.empty[String, (MalbaProtocol.AddTaskResponse, Deadline)]
  var commandIdForGetTask: Map[String, (MalbaProtocol.GetTaskResponseBase, Deadline)]   = Map.empty[String, (MalbaProtocol.GetTaskResponseBase, Deadline)]
  var commandIdForCancelTask: Map[String, (MalbaProtocol.CancelTaskResponse, Deadline)] = Map.empty[String, (MalbaProtocol.CancelTaskResponse, Deadline)]

  val cleanupTask          = context.system.scheduler.schedule(deleteAfter, deleteAfter, self, CleanupTick)

  val takeSnapshotSchedule = context.system.scheduler.schedule(snapshotInterval, snapshotInterval, self, TakeSnapshotTick)

  override def postStop(): Unit = {
    cleanupTask.cancel()
    takeSnapshotSchedule.cancel()
    ()
  }


  // persistenceId must include cluster role to support multiple masters
  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) => role + "-master"
    case None       => "master"
  }

  override def receiveRecover = {
    case event: MasterState.TaskAdded => 
      addTask(event)
    case event: MasterState.TaskSent =>
      getTask(event)
      ()
    case event: MasterState.TaskCanceledByGroup => 
      cancelTaskByGroup(event)
    case event: MasterState.TaskCanceledById => 
      cancelTaskById(event)
    case SnapshotOffer(metadata, state: MasterState) =>
      log.info(s"Recover from snapshot (metadata = ${metadata.toString})")
      masterState = state
    case RecoveryFailure(cause) =>
      log.error(cause, s"Failed to recover (persisten id = [${persistenceId}])")
      throw new Exception(s"Failed to recover (persisten id = [${persistenceId}])")
  }


  var masterState: MasterState = MasterState.empty

  def addTask(event: MasterState.TaskAdded) = {
    masterState = masterState.updated(event)
  }
  def cancelTaskById(event: MasterState.TaskCanceledById) = {
    masterState = masterState.updated(event)
  }
  def cancelTaskByGroup(event: MasterState.TaskCanceledByGroup) = {
    masterState = masterState.updated(event)
  }
  def getTask(event: MasterState.TaskSent): Option[Task] = {
    if(masterState.nonEmpty(event.taskType)){
      val ( task, state ) = masterState.dequeue(event.taskType)
      masterState = state
      task
    } else {
      None
    }
  }
  def setTaskTypeSetting( event: MasterState.TaskTypeSettingAdded ) = {
    masterState = masterState.updated(event)
  }

  override def receiveCommand = {
    case MalbaProtocol.AddTaskRequest ( id, taskId, from, group, option, taskType, task ) =>
      if(commandIdForAddTask.isDefinedAt(id)){
        val response: MalbaProtocol.AddTaskResponse = commandIdForAddTask.apply( id )._1
        sender() ! response
      } else {
        if(masterState.contains(taskType, taskId)){
          val response = MalbaProtocol.AddTaskResponse(id, taskId, from, group, taskType, MalbaProtocol.Reject("409", "Duplicate task id"), 0L, 0L, 0L)
          commandIdForAddTask = commandIdForAddTask + (id -> Tuple2(response, getDeadline))
          sender() ! response
        } else {
          val event    = MasterState.TaskAdded(id, from, taskId, group, option, taskType, task)
          val response = MalbaProtocol.AddTaskResponse ( id, from, taskId,  group, taskType, MalbaProtocol.Ok, 0L, 0L, 0L ) // TODO: Implement seq number
          commandIdForAddTask = commandIdForAddTask + (id -> Tuple2(response, getDeadline))
          persist(event) { evt =>
            addTask(evt)
            sender() ! response
            context.parent ! MasterProtocol.Notify(evt.taskType)
          }
        }
      }

    case request @ MalbaProtocol.AddTaskWithCheckWorkState ( id, taskId, from, group, option, taskType, task ) =>
      if(commandIdForAddTask.isDefinedAt(id)){
        val response: MalbaProtocol.AddTaskResponse = commandIdForAddTask.apply( id )._1
        sender() ! response
      } else {
        if(masterState.contains(taskType, taskId)){
          val response = MalbaProtocol.AddTaskResponse(id, taskId, from, group, taskType, MalbaProtocol.Reject("409", "Duplicate task id"), 0L, 0L, 0L)
          commandIdForAddTask = commandIdForAddTask + (id -> Tuple2(response, getDeadline))
          sender() ! response
        } else {
          implicit val timeout = akka.util.Timeout(5.seconds)
          val fromActor = sender()
          (context.parent ? MalbaProtocol.GetWorkerStateRequest(taskType)).mapTo[MalbaProtocol.GetWorkerStateResponse].foreach{ workerStateRes =>
            val existsTaskId: Boolean = {
              workerStateRes.workerStateList.exists { 
                case state: MalbaProtocol.Busy => taskId == state.task.id
                case _ => false
              }
            }
            if(existsTaskId){
              val response = MalbaProtocol.AddTaskResponse(id, taskId, from, group, taskType, MalbaProtocol.Reject("409", "Duplicate task id"), 0L, 0L, 0L)
              commandIdForAddTask = commandIdForAddTask + (id -> Tuple2(response, getDeadline))
              fromActor ! response
            } else {
              self.tell(MalbaProtocol.AddTaskRequest ( id, taskId, from, group, option, taskType, task ), fromActor)
            }
          }
        }
      }

    case MalbaProtocol.GetTaskRequest( id, from, taskType ) =>
      if( commandIdForGetTask.isDefinedAt( id ) ) {
        val response: MalbaProtocol.GetTaskResponseBase = commandIdForGetTask.apply( id )._1
        sender() ! response
      } else {
        val event = MasterState.TaskSent( id, from, taskType )
        // Don't use psersistAsync because this message should be deal with synchronously.
        persist(event) { evt =>
          val taskOpt  = getTask( evt )
          val response = if( taskOpt.isDefined ){
            MalbaProtocol.GetTaskResponse ( evt.id, evt.from, evt.taskType, MalbaProtocol.Ok, taskOpt.get ) 
          } else {
            MalbaProtocol.GetNoTaskResponse ( evt.id, evt.from, evt.taskType ) 
          }
          val pair = ( response, getDeadline )
          commandIdForGetTask = commandIdForGetTask + ( evt.id -> pair )
          sender() ! response
        }
      }

    case MalbaProtocol.CancelTaskByIdRequest ( id, from, taskType, taskId )   =>
      if(commandIdForCancelTask.isDefinedAt(id)){
        val response: MalbaProtocol.CancelTaskResponse = commandIdForCancelTask.apply( id )._1
        sender() ! response
      } else {
        if(!masterState.contains(taskType, taskId)){
          val response = MalbaProtocol.CancelTaskByIdResponse(id, from, taskType, taskId, MalbaProtocol.Reject("404", "Not found task id"))
          commandIdForCancelTask = commandIdForCancelTask + (id -> Tuple2(response, getDeadline))
          sender() ! response
        } else {
          val event    = MasterState.TaskCanceledById(id, from, taskType, taskId)
          val response = MalbaProtocol.CancelTaskByIdResponse ( id, from, taskType, taskId, MalbaProtocol.Ok )
          commandIdForCancelTask = commandIdForCancelTask + (id -> Tuple2(response, getDeadline))
          persist(event) { evt =>
            cancelTaskById(evt)
            sender() ! response
          }
        }
      }

    case MalbaProtocol.CancelTaskByGroupRequest ( id, from, taskType, group ) =>
      if(commandIdForCancelTask.isDefinedAt(id)){
        val response: MalbaProtocol.CancelTaskResponse = commandIdForCancelTask.apply( id )._1
        sender() ! response
      } else {
        val event    = MasterState.TaskCanceledByGroup(id, from, taskType, group)
        val response = MalbaProtocol.CancelTaskByGroupResponse ( id, from, taskType, group, MalbaProtocol.Ok )
        commandIdForCancelTask = commandIdForCancelTask + (id -> Tuple2(response, getDeadline))
        persist(event) { evt =>
          cancelTaskByGroup(evt)
          sender() ! response
        }
      }

    case MalbaProtocol.PutTaskTypeSettingRequest ( taskType, maxNrOfWorkers, config, queueType, from ) =>
      val event = MasterState.TaskTypeSettingAdded(from, taskType, queueType, maxNrOfWorkers, config)
      persist(event) { evt =>
        setTaskTypeSetting( evt )
        sender() ! MalbaProtocol.PutTaskTypeSettingResponse ( evt.from, evt.taskType, MalbaProtocol.Ok )
      }

    case CleanupTick =>
      commandIdForAddTask = commandIdForAddTask.filterNot {
        case (_, (_, deadline)) => deadline.isOverdue
      }
      commandIdForGetTask = commandIdForGetTask.filterNot {
        case (_, (_, deadline)) => deadline.isOverdue
      }
      commandIdForCancelTask = commandIdForCancelTask.filterNot {
        case (_, (_, deadline)) => deadline.isOverdue
      }

    case TakeSnapshotTick =>
      saveSnapshot(masterState)

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
