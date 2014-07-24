package jp.co.shanon.malba.worker

import scala.collection.immutable.HashMap
import org.joda.time.DateTime

object WorkerState {

  def empty: WorkerState = WorkerState(HashMap.empty[String, Seq[String]])

  trait WorkerDomainEvent
  case class WorkerAdded(actorPath: String, taskType: String) extends WorkerDomainEvent
  case class WorkerKilled(actorPath: String, taskType: String) extends WorkerDomainEvent
}


case class WorkerState ( workerMap: HashMap[String, Seq[String]]) {

  import WorkerState._
  def contains(actorPath: String): Boolean = {
    workerMap.exists {
      case (key, workerList) => 
        workerList.exists(path => path == actorPath)
    }
  }

  def updated(event: WorkerDomainEvent): WorkerState = event match {
    case WorkerAdded(actorPath, taskType) =>
      val workers = workerMap.getOrElse(taskType, Seq.empty[String]) ++ Seq(actorPath)
      copy( workerMap = workerMap + (taskType -> workers))

    case WorkerKilled(actorPath, taskType) =>
      val newWorkerMap: HashMap[String, Seq[String]] = {
        val workers = workerMap.getOrElse(taskType, Seq.empty[String]).filterNot(_ == actorPath) 
        if(workers.isEmpty){
          workerMap.filterNot(pair => pair._1 == taskType)
        } else {
          workerMap + (taskType -> workers)
        }
      }
      copy(workerMap = newWorkerMap)
  }

}
