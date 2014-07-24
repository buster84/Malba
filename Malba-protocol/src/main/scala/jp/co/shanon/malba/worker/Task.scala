package jp.co.shanon.malba.worker

case class Task(
  id: String, 
  taskType: String, 
  task: String)

case object NoTask
