package jp.co.shanon.malba.worker
import akka.actor.ActorRef

object MasterProtocol {
  case class Notify(taskType: String)
  case class TaskRequest( taskType: String, actorPath: String, worker: ActorRef )
  case class TaskResponse( task: Task, actorPath: String, worker: ActorRef)
  case class NoTaskResponse(taskType: String, actorPath: String, worker: ActorRef)
}
