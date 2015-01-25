package jp.co.shanon.malba.worker
import jp.co.shanon.malba.queue.CustomQueue
import scala.collection.immutable.HashMap
import jp.co.shanon.malba.queue.FIFOQueue
import org.joda.time.DateTime
import scala.collection.mutable.PriorityQueue
import scala.math.Ordering

object QueueManagerState {
  def empty: QueueManagerState = QueueManagerState(HashMap.empty[String,(String,Map[String, String])], HashMap.empty[String, CustomQueue])

  trait QueueManagerDomainEvent
  case class TaskAdded(id: String, from: String, taskId: String, group: Option[String], option: Map[String, String], taskType: String, task: String) extends QueueManagerDomainEvent
  case class TaskCanceledById(id: String, from: String, taskType: String, taskId: String) extends QueueManagerDomainEvent
  case class TaskCanceledByGroup(id: String, from: String, taskType: String, group: String) extends QueueManagerDomainEvent
  case class TaskSent(id: String, from: String, taskType: String) extends QueueManagerDomainEvent
  case class TaskTypeSettingAdded(from: String, taskType: String, queueType: String, maxNrOfWorkers: Int, config: Map[String, String]) extends QueueManagerDomainEvent
}


case class QueueManagerState(
  taskTypeSetting: HashMap[String, ( String, Map[String, String] )],
  tasks: HashMap[String, CustomQueue]
) {
  import QueueManagerState._

  def setTaskTypeSetting( taskType: String, queueType: String, maxNrOfWorkers: Int, config: Map[String, String] ): QueueManagerState = {
    copy(taskTypeSetting = taskTypeSetting + ( taskType -> Tuple2(queueType, config) ))
  }

  def getInitialQueue( taskType: String ): CustomQueue = {
    val (queueName, config) = taskTypeSetting.getOrElse( taskType, Tuple2("jp.co.shanon.malba.queue.FIFOQueue", Map.empty[String, String]) )
    try {
      Class.forName(queueName).getConstructor(classOf[Map[String, String]]).newInstance( config ).asInstanceOf[CustomQueue]
    } catch {
      case e: Exception => 
        println( e.getMessage )
        new FIFOQueue(Map.empty[String, String])
    }
  }

  def nonEmpty(taskType: String): Boolean = {
    tasks.isDefinedAt(taskType)
  }

  def contains(taskType: String, taskId: String): Boolean = {
    nonEmpty(taskType) && tasks.apply(taskType).contains(taskId)
  }

  def isEnqueueable( taskId: String, taskType: String, content: String, group: Option[String], option: Map[String, String] ): Boolean = {
    val task = Task( taskId, taskType, content )
    // Because default Queue is FIFOQueue and empty FIFOQueue can enqueue any tasks, if given taskType is empty, any task can be enqueued
    !nonEmpty( taskType ) || tasks.apply(taskType).isEnqueueable( task, group, option )
  }

  def enqueue( taskId: String, taskType: String, content: String, group: Option[String], option: Map[String, String] ): QueueManagerState = {
    val task = Task( taskId, taskType, content )
    if( nonEmpty( taskType ) ){
      tasks.apply(taskType).enqueue( task, group, option )
      this
    } else {
      val taskList = getInitialQueue( taskType )
      taskList.enqueue( task, group, option )
      val newTasks = tasks + ( taskType -> taskList )
      copy(tasks = newTasks)
    }
  }

  def dequeue(taskType: String): (Option[Task], QueueManagerState) = {
    val taskList = tasks.getOrElse(taskType, getInitialQueue( taskType ))
    if(taskList.isEmpty){
      (None, this)
    } else {
      val task = taskList.dequeue()
      if(taskList.isEmpty){
        (Some(task), copy( tasks =  tasks - taskType))
      } else {
        (Some(task), this)
      }
    }
  }

  def deleteById( taskType: String, id: String ): QueueManagerState = {
    val taskList = tasks.getOrElse(taskType, getInitialQueue( taskType ))
    taskList.deleteById( id )
    if( taskList.isEmpty ){
      copy( tasks =  tasks - taskType)
    } else {
      this
    }
  }

  def deleteByGroup( taskType: String, group: String ): QueueManagerState = {
    val taskList = tasks.getOrElse(taskType, getInitialQueue( taskType ))
    taskList.deleteByGroup( group )
    if( taskList.isEmpty ){
      copy( tasks =  tasks - taskType)
    } else {
      this
    }
  }


  def updated(event: QueueManagerDomainEvent): QueueManagerState = {
    event match {
      case TaskAdded(id, from, taskId, group, option, taskType, task) => 
        enqueue( taskId, taskType, task, group, option)
      case TaskCanceledById(id, from, taskType, taskId) => 
        deleteById( taskType, taskId )
      case TaskCanceledByGroup(id, from, taskType, group) => 
        deleteByGroup( taskType, group )
      case TaskSent(id, from, taskType) => 
        val ( task, state ) = dequeue( taskType )
        state
      case TaskTypeSettingAdded(from, taskType, queueType, maxNrOfWorkers, config) => 
        setTaskTypeSetting( taskType, queueType, maxNrOfWorkers, config )
    }
  }
}
