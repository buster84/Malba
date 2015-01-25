package jp.co.shanon.malba.queue
import jp.co.shanon.malba.worker.Task

abstract class CustomQueue( config: Map[String, String] ) extends scala.Serializable {
  def contains(id: String): Boolean
  def isEnqueueable( task: Task, group: Option[String], option: Map[String, String] ): Boolean
  def enqueue( task: Task, group: Option[String], option: Map[String, String] ): Unit
  def isEmpty: Boolean
  def dequeue(): Task
  def deleteById( id: String ): Unit
  def deleteByGroup( group: String ): Unit
  def equals( obj: Any ): Boolean
  def == = equals _
}
