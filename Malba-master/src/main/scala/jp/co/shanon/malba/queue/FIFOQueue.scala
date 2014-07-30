package jp.co.shanon.malba.queue
import jp.co.shanon.malba.worker.Task

class FIFOQueue( config: Map[String, String] = Map.empty[String, String] ) extends CustomQueue( config ) {
  private var storage: List[(String, String, Task)] = List.empty[(String, String, Task)]

  def contains(id: String): Boolean = {
    storage.exists{
      case ( taskId, _, _ ) => taskId == id
    }
  }

  def isEmpty: Boolean = {
    storage.isEmpty
  }

  def enqueue( task: Task, group: Option[String], option: Map[String, String] ): Unit = {
    val insertingGroup = group.getOrElse("OTHERS")
    storage = storage ++ Seq((task.id, insertingGroup, task))
  }

  // Should call nonEmpty before calling dequeue 
  def dequeue(): Task = {
    val ( id, group, task ) = storage.head
    storage = storage.tail
    task
  }

  def deleteById( id: String ): Unit = {
    storage = storage.filterNot{
      case ( taskId, _, _ ) => taskId == id
    }
  }

  def deleteByGroup( group: String ): Unit = {
    storage = storage.filterNot{
      case ( _, g, _ ) => g == group
    }
  }

  def getStorage = storage

  override def equals( obj: Any ): Boolean = {
    obj match {
      case o: FIFOQueue => o.getStorage == storage
      case _ => false
    }
  }
}
