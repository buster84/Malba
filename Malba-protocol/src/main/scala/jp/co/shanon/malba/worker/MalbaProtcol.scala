package jp.co.shanon.malba.worker

object MalbaProtocol {
  //
  // Add tasks protocol
  //
  case class AddTaskRequest (
    id: String,
    from: String,
    group: Option[String],
    taskType: String,
    task: String
  )

  case class AddTaskResponse (
    id: String,
    from: String,
    group: Option[String],
    taskType: String,
    status: Status,
    seqNumInGroup: Long,      // 
    minSeqNum: Long,
    maxSeqNum: Long
  )

  sealed trait Status {
    val code : String
  }
  case object Ok extends Status {
    val code : String = "200"
  }
  case object Accepted extends Status {
    val code : String = "202"
  }
  case object NoContent extends Status {
    val code : String = "204"
  }
  case class Reject(code: String, message: String) extends Status

  
  // 
  // Get tasks protcol
  // 
  case class GetTaskRequest (
    id: String,
    from: String,
    taskType: String
  )

  sealed trait GetTaskResponseBase {
    val id: String
    val from: String
    val taskType: String
    val status: Status
  }
  case class GetTaskResponse (
    id: String,
    from: String,
    taskType: String,
    status: Status,
    task: Task
  ) extends GetTaskResponseBase
  case class GetNoTaskResponse (
    id: String,
    from: String,
    taskType: String
  ) extends GetTaskResponseBase {
    val status: Status = NoContent
  }



  // 
  // Cancel tasks protcol
  // 
  sealed trait CancelTaskRequest {
    val id: String
    val from: String
    val taskType: String
  }

  case class CancelTaskByIdRequest (
    id: String,
    from: String,
    taskType: String,
    taskId: String
  ) extends CancelTaskRequest

  case class CancelTaskByGroupRequest (
    id: String,
    from: String,
    taskType: String,
    group: String
  ) extends CancelTaskRequest

  sealed trait CancelTaskResponse {
    val id: String
    val taskType: String
    val from: String
    val status: Status
  }

  case class CancelTaskByIdResponse (
    id: String,
    from: String,
    taskType: String,
    taskId: String,
    status: Status
  ) extends CancelTaskResponse

  case class CancelTaskByGroupResponse (
    id: String,
    from: String,
    taskType: String,
    group: String,
    status: Status
  ) extends CancelTaskResponse

  //
  // Put (Upsert) task type Setting 
  //
  case class PutTaskTypeSettingRequest (
    taskType: String,
    maxNrOfWorkers: Int, // TODO: Not sure that it should be implemented later or future.
    queueType: String,
    from: String
  )

  case class PutTaskTypeSettingResponse (
    from: String,
    taskType: String,
    status: Status
  )

  //
  // Add worker
  //
  case class AddMeAsWorkerRequest (
    id: String,
    taskType: String
  )
  case class AddWorkerRequest (
    id: String,
    taskType: String,
    actorPath: String    // ex. "akka.tcp://my-sys@host.example.com:5678/user/service-b"
  )
  case class AddWorkerResponse (
    id: String,
    taskType: String,
    actorPath: String,
    status: Status
  )

  //
  // Internal server error 
  //
  case class IntenalServerError(message: String){
    val code = "500"
  }
}
