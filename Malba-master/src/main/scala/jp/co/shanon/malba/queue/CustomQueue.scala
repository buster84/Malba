package jp.co.shanon.malba.queue
import jp.co.shanon.malba.worker.Task

trait CustomQueue extends scala.Serializable {
  def enqueue( task: Task, group: Option[String] ): Unit
  def isEmpty: Boolean
  def dequeue(): Task
  def deleteById( id: String ): Unit
  def deleteByGroup( group: String ): Unit
  def equals( obj: Any ): Boolean
  def == = equals _
}
