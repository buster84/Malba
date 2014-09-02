package controllers

import play.api._
import play.api.mvc._
import play.api.Play
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import jp.co.shanon.malba.client.MalbaClientPlugin
import jp.co.shanon.malba.worker.MalbaProtocol
import java.util.UUID

object Application extends Controller {

  def index = Action.async {
    MalbaClientPlugin(current).addTask(
      taskId = UUID.randomUUID().toString, 
      group = None, 
      option = Map.empty[String, String], 
      taskType = "test",
      task = ""
    ).map {
      case MalbaProtocol.Ok => 
        Ok("ok")
      case MalbaProtocol.Reject(code, message) =>
        Ok(s"${code} ${message}")
      case s =>
        Ok("Something wrong")
    }
  }
}
