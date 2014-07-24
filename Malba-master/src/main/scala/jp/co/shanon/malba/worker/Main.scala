package jp.co.shanon.malba.worker

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
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.Identify
import akka.actor.ActorIdentity

object Main {

  def main(args: Array[String]): Unit = {
    val workerManagerId = "workerManagerId"
    val role            = "backend"

    val conf = if (args.isEmpty) {
      ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
        withFallback(ConfigFactory.load())
    } else {
      val port            = args(0)
      ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
        withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
        withFallback(ConfigFactory.load())
    }

    val system = ActorSystem("MalbaSystem", conf)

    system.actorOf(ClusterSingletonManager.props(Master.props(workerManagerId), "active", PoisonPill, Some(role)), "master")
    system.actorOf(Props[Frontend], "frontend")
    ()
  }
}
