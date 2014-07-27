package jp.co.shanon.malba.queue
import jp.co.shanon.malba.worker.Task
import scala.collection.mutable.LinkedHashMap

class BalancingQueue( config: Map[String, String] = Map.empty[String, String] ) extends CustomQueue( config ) {
  private var storage: Seq[(String,LinkedHashMap[String, Task])] = Seq.empty[(String,LinkedHashMap[String, Task])]

  def isEmpty: Boolean = {
    storage.isEmpty
  }

  def contains(id: String): Boolean = {
    storage.exists{
      case ( _, list) => list.isDefinedAt(id)
    }
  }

  def enqueue( task: Task, group: Option[String], option: Map[String, String] ): Unit = {
    val insertingGroup = group.getOrElse( "OTHERS" )
    if(storage.exists{ case (g, _) => g == insertingGroup }){
      storage = storage.collect {
        case (g, list) if g == insertingGroup => 
          list += ( task.id -> task )
          (g, list)
        case rest => rest
      }
    } else {
      storage = storage ++ Seq((insertingGroup, LinkedHashMap(task.id -> task)))
    }
  }

  // Should call nonEmpty before calling dequeue 
  def dequeue(): Task = {
    val (group, list)  = storage.head
    val restStorage    = storage.tail

    val ( id, task )   = list.head
    list -= id
    if(list.nonEmpty) {
      storage = restStorage ++ Seq((group, list))
    } else {
      storage = restStorage
    }
    task
  }

  def deleteById( id: String ): Unit = {
    storage = storage.map{
      case ( group, list ) => 
        list -= id
        ( group, list )
    }
  }

  def deleteByGroup( group: String ): Unit = {
    storage = storage.filterNot{
      case ( g, list ) => g == group
    }
  }

  def getStorage = storage

  override def equals( obj: Any ): Boolean = {
    obj match {
      case o: BalancingQueue => o.getStorage == storage
      case _ => false
    }
  }
}
