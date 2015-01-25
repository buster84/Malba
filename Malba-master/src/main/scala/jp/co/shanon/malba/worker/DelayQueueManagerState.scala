package jp.co.shanon.malba.worker
import jp.co.shanon.malba.queue.CustomQueue
import scala.collection.immutable.HashMap
import jp.co.shanon.malba.queue.FIFOQueue
import org.joda.time.DateTime
import scala.collection.mutable.PriorityQueue
import scala.math.Ordering
import scala.annotation.tailrec
import MalbaProtocol.AddDelayTaskRequest
import scala.collection.mutable.ListBuffer

object DelayQueueManagerState {
  object AddDelayTaskRequestOrdering extends Ordering[AddDelayTaskRequest] {
    def compare( a: AddDelayTaskRequest, b: AddDelayTaskRequest ): Int = {
      if( a.scheduledTime.isBefore( b.scheduledTime ) ){
        1
      } else {
        -1
      }
    }
  }
  def empty: DelayQueueManagerState = new DelayQueueManagerState()

  trait DelayQueueManagerDomainEvent

  case class DelayTaskAdded(request: AddDelayTaskRequest) extends DelayQueueManagerDomainEvent
  case class DelayTasksSent( now: DateTime ) extends DelayQueueManagerDomainEvent
  case class DelayTaskCanceledById(id: String, from: String, taskType: String, taskId: String) extends DelayQueueManagerDomainEvent
  case class DelayTaskCanceledByGroup(id: String, from: String, taskType: String, group: String) extends DelayQueueManagerDomainEvent
}


class DelayQueueManagerState {
  import DelayQueueManagerState._

  var addDelayTaskRequestList: PriorityQueue[AddDelayTaskRequest] = new PriorityQueue()(AddDelayTaskRequestOrdering)

  def existsEqueueableTask( now: DateTime = DateTime.now ): Boolean = {
    addDelayTaskRequestList.headOption.map { req =>
      req.scheduledTime.isBefore( now )
    }.getOrElse( false )
  }

  def dequeueTaskRequest(): ( AddDelayTaskRequest, DelayQueueManagerState ) = {
    val task = addDelayTaskRequestList.dequeue
    ( task, this )
  }

  def takeAllEnqueueableTasks( now: DateTime ): ( List[AddDelayTaskRequest], DelayQueueManagerState ) = {
    val listBuilder = ListBuffer.empty[AddDelayTaskRequest]
    while( existsEqueueableTask( now ) ){
      val ( task, _ ) = dequeueTaskRequest()
      listBuilder += task
    }
    ( listBuilder.toList, this )
  }

  def enqueueTaskRequest( request: AddDelayTaskRequest ): DelayQueueManagerState = {
    addDelayTaskRequestList.enqueue( request )
    this
  }

  def contains( taskType: String, taskId: String ): Boolean = {
    addDelayTaskRequestList.exists( req => req.taskType == taskType && req.taskId == taskId )
  }

  def deleteTaskById( taskType: String, taskId: String ): DelayQueueManagerState = {
    addDelayTaskRequestList = addDelayTaskRequestList.filter( req => req.taskType != taskType || req.taskId != taskId )
    this
  }

  def deleteTaskByGroup( taskType: String, group: String ): DelayQueueManagerState = {
    addDelayTaskRequestList = addDelayTaskRequestList.filter( req => req.taskType != taskType || req.group != Some( group ) )
    this
  }

  def updated(event: DelayQueueManagerDomainEvent): DelayQueueManagerState = {
    event match {
      case DelayTaskAdded(request: AddDelayTaskRequest) =>
        enqueueTaskRequest( request )
      case DelayTasksSent( now ) =>
        val (_, state) = takeAllEnqueueableTasks(now)
        state
      case DelayTaskCanceledById(id: String, from: String, taskType: String, taskId: String) =>
        deleteTaskById( taskType, taskId )
      case DelayTaskCanceledByGroup(id: String, from: String, taskType: String, group: String) =>
        deleteTaskByGroup( taskType, group )
    }
  }
}
