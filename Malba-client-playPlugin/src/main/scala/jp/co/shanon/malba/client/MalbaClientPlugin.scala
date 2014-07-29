package jp.co.shanon.malba.client
import play.api.Play
import play.api.libs.concurrent.Akka 
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Plugin
import play.api.Application
import play.api.Logger
import akka.actor.ActorRef
import scala.concurrent._
import scala.concurrent.duration._

class MalbaClientPlugin(implicit app: Application) extends Plugin {
  private var from : String = _
  private val timeout : FiniteDuration = Duration(app.configuration.getInt("malbaClient.timeout.seconds").getOrElse(5), SECONDS)
  private var maxRetry : Int = app.configuration.getInt("malbaClient.timeout.seconds").getOrElse(8)

  lazy val client = new MalbaClient(Akka.system(app), from, timeout, maxRetry)

  override def onStart() {
    Logger.info("[MalbaClientPlugin] initializing: %s".format(this.getClass))
    from = app.configuration.getString("malbaClient.from").getOrElse(throw new RuntimeException("Need to set 'malbaClient.from' configuration."))
    ()
  }
}

object MalbaClientPlugin {
  def apply(implicit app: Application): MalbaClient = {
    app.plugin[MalbaClientPlugin] match {
      case Some(plugin) => plugin.client
      case None => throw new RuntimeException("There is no MalbaClientPlugin registerd.")
    }
  }
}
