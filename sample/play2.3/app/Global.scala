import play.api._
import play.api.Play
import play.api.Play.current
import scala.concurrent._
import scala.concurrent.duration._
import play.api.libs.concurrent.Akka 
import com.typesafe.config.ConfigFactory
import jp.co.shanon.malba.client.MalbaClient
import jp.co.shanon.malba.client.MalbaClientPlugin
import jp.co.shanon.malba.client.MalbaWorkManager

import akka.actor.ActorRef
import akka.actor.ActorSystem

object Global extends GlobalSettings {
  val systemName = "TestApp"
  lazy val system = ActorSystem(systemName, ConfigFactory.load.getConfig(systemName))

  // Worker
  var workers: Seq[ActorRef] = Seq.empty[ActorRef]

  lazy val workerProps = MalbaWorkManager.props( 
    malbaClient       = MalbaClientPlugin(current),
    taskType          = "test",
    workExecutorProps = actors.Worker.props(),
    pollingInterval   = Duration(30, SECONDS),
    workTimeout       = Duration.Inf,
    maxRetryCount     = 0 )

  override def onStart(app: Application) {
    import ExecutionContext.Implicits.global
    workers = Seq(system.actorOf(workerProps))
  }
  override def onStop(app: Application) {
    workers.foreach{ ref => MalbaWorkManager.setStopping(ref) }
    workers.foreach{ ref => MalbaWorkManager.awaitTermination(ref) }
  }
}
