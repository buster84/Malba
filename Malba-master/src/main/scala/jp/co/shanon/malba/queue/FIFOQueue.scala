package jp.co.shanon.malba.queue
import jp.co.shanon.malba.worker.Task
import scala.collection.immutable.Queue

/**
 * First In, First Out Queue.
 * FIFOQueue provides following efficient operations.
 * - contains
 * - enqueue
 * - dequeue
 * But other operations(deleteById, deleteByGroup) may require much time.
 */
class FIFOQueue( config: Map[String, String] = Map.empty[String, String] ) extends CustomQueue( config ) {
  private[this] type TaskId = String
  private[this] type Group = String

  private var storage: Queue[(Group, Task)] = Queue.empty
  // TaskId -> count of Task
  private[this] var taskCounter: Map[TaskId, Int] = Map.empty
  private[this] def updateCounter(id: TaskId, increment: Int): Unit = {
    val newCount = taskCounter.getOrElse(id, 0) + increment
    if (newCount == 0) {
      taskCounter -= id
    } else {
      taskCounter += id -> newCount
    }
  }

  def contains(id: String): Boolean = {
    taskCounter.contains(id)
  }

  def isEnqueueable( task: Task, group: Option[String], option: Map[String, String] ): Boolean = {
    ! contains( task.id )
  }

  def isEmpty: Boolean = {
    storage.isEmpty
  }

  def enqueue( task: Task, group: Option[String], option: Map[String, String] ): Unit = {
    val insertingGroup = group.getOrElse("OTHERS")
    storage = storage.enqueue((insertingGroup, task))
    updateCounter(task.id, increment = 1)
  }

  // Should call nonEmpty before calling dequeue 
  def dequeue(): Task = {
    val (( _, task ), queue) = storage.dequeue
    storage = queue
    updateCounter(task.id, increment = -1)
    task
  }

  def deleteById( id: String ): Unit = {
    if (contains(id)) {
      storage = storage.filterNot {
        case ( _, task ) =>
          if (task.id == id) {
            updateCounter(id, increment = -1)
            true
          } else {
            false
          }
      }
    }
  }

  def deleteByGroup( group: String ): Unit = {
    storage = storage.filterNot{
      case ( g, task ) =>
        if (g == group) {
          updateCounter(task.id, increment = -1)
          true
        } else {
          false
        }
    }
  }

  override def equals( obj: Any ): Boolean = {
    obj match {
      case o: FIFOQueue => o.storage == storage
      case _ => false
    }
  }
}
