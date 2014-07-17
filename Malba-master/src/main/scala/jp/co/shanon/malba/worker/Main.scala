package worker

import scala.concurrent._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.RootActorPath
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterClient
import akka.contrib.pattern.ClusterSingletonManager
import akka.japi.Util.immutableSeq
import akka.actor.AddressFromURIString
import akka.actor.ActorPath
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.Identify
import akka.actor.ActorIdentity
import akka.persistence.journal.leveldb.SharedLeveldbJournal

object Main {

  def main(args: Array[String]): Unit = {
    /**
    if (args.isEmpty) {
      startBackend(2551, "backend")
      Thread.sleep(10000)
      startBackend(2552, "backend")
      Thread.sleep(10000)
      implicit val t = akka.util.Timeout(5, SECONDS)

      val frontend = startFrontend(2553)
      // Thread.sleep(5000)
      // println(Await.result((frontend ? Work("11", "aaa")), Duration(5, SECONDS)).toString)
      // println(Await.result((frontend ? Work("21", "bbb")), Duration(5, SECONDS)).toString)
      // println(Await.result((frontend ? "GET_WORKS"), Duration(5, SECONDS)).toString)
      ()
    } else {
      val port = args(0).toInt
      startBackend(port, "backend")
      startFrontend(port)
      ()
    }
    **/
    println("Not yet")
    ()
  }

  /*
  def workTimeout = 10.seconds

  def startBackend(port: Int, role: String): Unit = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", conf)

    system.actorOf(ClusterSingletonManager.props(Master.props(workTimeout), "active",
      PoisonPill, Some(role)), "master")
    ()
  }

  def startFrontend(port: Int) = {
    val conf     = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.load())
    val system   = ActorSystem("ClusterSystem", conf)
    val frontend = system.actorOf(Props[Frontend], "frontend")
    frontend 
  }
  */

}
