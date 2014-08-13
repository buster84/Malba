package jp.co.shanon.malba.worker

import scala.concurrent.duration._
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.remote.RemoteActorRefProvider
import akka.testkit.TestKit
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import akka.contrib.pattern.DistributedPubSubMediator.SubscribeAck
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSingletonManager
import akka.actor.Props
import akka.actor.PoisonPill
import akka.actor.RootActorPath
import akka.contrib.pattern.ClusterClient
import akka.actor.ActorRef
import akka.actor.Actor
import akka.testkit.ImplicitSender
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import java.io.File
import org.apache.commons.io.FileUtils

object MasterSpec {

  val clusterConfig = ConfigFactory.parseString("""
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.remote.netty.tcp.port=0
    akka.remote.netty.tcp.hostname=localhost
    akka.cluster {
      seed-nodes = [
        "akka.tcp://MasterSpec@localhost:"2554,
        "akka.tcp://MasterSpec@localhost:"2555,
        "akka.tcp://MasterSpec@localhost:"2556 ]
      auto-down-unreachable-after = 5s
    }
    akka.extensions = ["akka.contrib.pattern.ClusterReceptionistExtension"]
    akka.persistence {
      journal.leveldb {
        native = off
        dir = "target/test-journal"
      }
      snapshot-store.local.dir = "target/test-snapshots"
    }
    """)

  val taskExecuterConfig = ConfigFactory.parseString("""
    akka.actor.provider = "akka.remote.RemoteActorRefProvider"
    akka.remote.netty.tcp.hostname=localhost
    akka.remote.netty.tcp.port=2553
    """)

  val actorPath: String = "akka.tcp://TaskExecuterSpec@localhost:2554/user/taskExecuter"
  val taskType: String  = "testTaskType"
  var requestid         = "id1"
  val from              = "from"

  class WorkExecutor(ref: ActorRef) extends Actor {
    def receive = {
      case WorkerProtocol.TaskIsReady =>
        sender() ! MalbaProtocol.GetTaskRequest(requestid, from, taskType)
      case m: MalbaProtocol.GetTaskResponse =>
        ref ! "task"
      case m: MalbaProtocol.GetNoTaskResponse =>
        ref ! "notask"
    }
  }
}

class MasterSpec(var _system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll
  with ImplicitSender {

  def this() = this{
    val config = ConfigFactory.parseString("""akka.remote.netty.tcp.port=2554""").withFallback(MasterSpec.clusterConfig)
    ActorSystem("MasterSpec", config)
  }

  val testProbe = TestProbe()


  val taskExecuterSystem: ActorSystem = ActorSystem("TaskExecuterSpec",MasterSpec.taskExecuterConfig)

  val backendSystem: ActorSystem = {
    val config = ConfigFactory.parseString("""akka.remote.netty.tcp.port=2555
                                              akka.cluster.roles=[backend]""").withFallback(MasterSpec.clusterConfig)
    ActorSystem("MasterSpec", config)
  }
  val backendSystem2: ActorSystem = {
    val config = ConfigFactory.parseString("""akka.remote.netty.tcp.port=2556
                                              akka.cluster.roles=[backend]""").withFallback(MasterSpec.clusterConfig)
    ActorSystem("MasterSpec", config)
  }

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))


  override def beforeAll: Unit = {
    storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
  }

  override def afterAll: Unit = {
    system.shutdown()
    if( ! backendSystem.isTerminated  ) {
      backendSystem.shutdown()
      backendSystem.awaitTermination()
    }
    if( ! backendSystem2.isTerminated  ) {
      backendSystem2.shutdown()
      backendSystem2.awaitTermination()
    }

    taskExecuterSystem.shutdown()
    system.awaitTermination()
    taskExecuterSystem.awaitTermination()

    storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
  }

  "Master" should "work as queuing server" in {

    val singletonManager1 = backendSystem.actorOf(ClusterSingletonManager.props(Props( classOf[Master], "workerManager1" ), "active",
      PoisonPill, Some("backend"), 5, 4), "master")
    val singletonManager2 = backendSystem2.actorOf(ClusterSingletonManager.props(Props( classOf[Master], "workerManager1" ), "active",
      PoisonPill, Some("backend"), 5, 4), "master")

    val frontend = system.actorOf(Props[Frontend], "frontend")

    val taskExecuter  = taskExecuterSystem.actorOf(Props(classOf[MasterSpec.WorkExecutor], testProbe.ref), "taskExecuter1")
    val actorPath     = MasterSpec.actorPath + "1"

    // Add task
    within(20.seconds) {
      awaitAssert {
        frontend ! MalbaProtocol.AddTaskRequest ( "id1","id1", "from1", None, Map.empty[String, String], "taskType1", "contents1")
        expectMsg(MalbaProtocol.AddTaskResponse ( "id1", "from1", "id1",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
        frontend ! MalbaProtocol.AddTaskRequest ( "id1","id1", "from1", None, Map.empty[String, String], "taskType1", "contents1")
        expectMsg(MalbaProtocol.AddTaskResponse ( "id1", "from1", "id1",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
        frontend ! MalbaProtocol.AddTaskRequest ( "id2","id2", "from1", None, Map.empty[String, String], "taskType1", "contents2")
        expectMsg(MalbaProtocol.AddTaskResponse ( "id2", "from1", "id2",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))

        frontend ! MalbaProtocol.AddTaskRequest ( "id3","id3", "from1", None, Map.empty[String, String], "taskType2", "contents3")
        expectMsg(MalbaProtocol.AddTaskResponse ( "id3", "from1", "id3",None, "taskType2", MalbaProtocol.Ok, 0L, 0L, 0L))

        frontend ! MalbaProtocol.AddTaskWithCheckWorkState ( "id1a","id1a", "from1", None, Map.empty[String, String], "taskType1a", "contents1a")
        expectMsg(MalbaProtocol.AddTaskResponse ( "id1a", "from1", "id1a",None, "taskType1a", MalbaProtocol.Ok, 0L, 0L, 0L))
        frontend ! MalbaProtocol.AddTaskWithCheckWorkState ( "id1a","id1a", "from1", None, Map.empty[String, String], "taskType1a", "contents1a")
        expectMsg(MalbaProtocol.AddTaskResponse ( "id1a", "from1", "id1a",None, "taskType1a", MalbaProtocol.Ok, 0L, 0L, 0L))
        frontend ! MalbaProtocol.AddTaskWithCheckWorkState ( "id2a","id2a", "from1", None, Map.empty[String, String], "taskType1a", "contents2a")
        expectMsg(MalbaProtocol.AddTaskResponse ( "id2a", "from1", "id2a",None, "taskType1a", MalbaProtocol.Ok, 0L, 0L, 0L))

        frontend ! MalbaProtocol.AddTaskWithCheckWorkState ( "id3a","id3a", "from1", None, Map.empty[String, String], "taskType2a", "contents3a")
        expectMsg(MalbaProtocol.AddTaskResponse ( "id3a", "from1", "id3a",None, "taskType2a", MalbaProtocol.Ok, 0L, 0L, 0L))
      }
    }

    // Get task
    frontend ! MalbaProtocol.GetTaskRequest ( "id4", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetTaskResponse ( "id4", "from1", "taskType1", MalbaProtocol.Ok, Task( "id1", "taskType1", "contents1" )))
    frontend ! MalbaProtocol.GetTaskRequest ( "id4", "from1", "taskType1")                                                             // For idempotent
    expectMsg(MalbaProtocol.GetTaskResponse ( "id4", "from1", "taskType1", MalbaProtocol.Ok, Task( "id1", "taskType1", "contents1" ))) // For idempotent
    frontend ! MalbaProtocol.GetTaskRequest ( "id5", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetTaskResponse ( "id5", "from1", "taskType1", MalbaProtocol.Ok, Task( "id2", "taskType1", "contents2" )))
    frontend ! MalbaProtocol.GetTaskRequest ( "id6", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetNoTaskResponse ( "id6", "from1", "taskType1"))

    frontend ! MalbaProtocol.GetTaskRequest ( "id7", "from1", "taskType2")
    expectMsg(MalbaProtocol.GetTaskResponse ( "id7", "from1", "taskType2", MalbaProtocol.Ok, Task( "id3", "taskType2", "contents3" )))
    frontend ! MalbaProtocol.GetTaskRequest ( "id7", "from1", "taskType2")                                                             // For idempotent
    expectMsg(MalbaProtocol.GetTaskResponse ( "id7", "from1", "taskType2", MalbaProtocol.Ok, Task( "id3", "taskType2", "contents3" ))) // For idempotent
    frontend ! MalbaProtocol.GetTaskRequest ( "id8", "from1", "taskType2")
    expectMsg(MalbaProtocol.GetNoTaskResponse ( "id8", "from1", "taskType2"))


    // Cancel by id
    frontend ! MalbaProtocol.AddTaskRequest ( "id9", "id9", "from1", None, Map.empty[String, String], "taskType1", "contents9")
    expectMsg(MalbaProtocol.AddTaskResponse ( "id9", "from1", "id9", None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddTaskRequest ( "id10","id10", "from1", None, Map.empty[String, String], "taskType1", "contents10")
    expectMsg(MalbaProtocol.AddTaskResponse ( "id10", "from1","id10", None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddTaskRequest ( "id11","id11", "from1", None, Map.empty[String, String], "taskType1", "contents11")
    expectMsg(MalbaProtocol.AddTaskResponse ( "id11", "from1", "id11",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))

    frontend ! MalbaProtocol.CancelTaskByIdRequest ( "id12", "from1", "taskType1", "id10" )
    expectMsg(MalbaProtocol.CancelTaskByIdResponse ( "id12", "from1", "taskType1", "id10", MalbaProtocol.Ok ))

    frontend ! MalbaProtocol.GetTaskRequest ( "id12", "from1", "taskType1")                                                             
    expectMsg(MalbaProtocol.GetTaskResponse ( "id12", "from1", "taskType1", MalbaProtocol.Ok, Task( "id9", "taskType1", "contents9" ))) 
    frontend ! MalbaProtocol.GetTaskRequest ( "id13", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetTaskResponse ( "id13", "from1", "taskType1", MalbaProtocol.Ok, Task( "id11", "taskType1", "contents11" )))
    frontend ! MalbaProtocol.GetTaskRequest ( "id14", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetNoTaskResponse ( "id14", "from1", "taskType1"))


    // Cancel by group
    frontend ! MalbaProtocol.AddTaskRequest ( "id15","id15", "from1", Some( "group1" ), Map.empty[String, String], "taskType1", "contents15")
    expectMsg(MalbaProtocol.AddTaskResponse ( "id15", "from1","id15", Some( "group1" ), "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddTaskRequest ( "id16","id16", "from1", Some( "group1" ), Map.empty[String, String], "taskType1", "contents16")
    expectMsg(MalbaProtocol.AddTaskResponse ( "id16", "from1","id16", Some( "group1" ), "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddTaskRequest ( "id17","id17", "from1", Some( "group1" ), Map.empty[String, String], "taskType1", "contents17")
    expectMsg(MalbaProtocol.AddTaskResponse ( "id17", "from1","id17", Some( "group1" ), "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))

    frontend ! MalbaProtocol.CancelTaskByGroupRequest ( "id18", "from1", "taskType1", "group1" )
    expectMsg(MalbaProtocol.CancelTaskByGroupResponse ( "id18", "from1", "taskType1", "group1", MalbaProtocol.Ok ))

    frontend ! MalbaProtocol.GetTaskRequest ( "id19", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetNoTaskResponse ( "id19", "from1", "taskType1"))

    // Check persistence
    frontend ! MalbaProtocol.AddTaskRequest ( "id20", "id20", "from1", Some( "group1" ), Map.empty[String, String], "taskType1", "contents20")
    expectMsg(MalbaProtocol.AddTaskResponse ( "id20", "from1", "id20", Some( "group1" ), "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddTaskRequest ( "id21", "id21", "from1", Some( "group1" ), Map.empty[String, String], "taskType1", "contents21")
    expectMsg(MalbaProtocol.AddTaskResponse ( "id21", "from1", "id21", Some( "group1" ), "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddTaskRequest ( "id22", "id22", "from1", Some( "group1" ), Map.empty[String, String], "taskType1", "contents22")
    expectMsg(MalbaProtocol.AddTaskResponse ( "id22", "from1", "id22", Some( "group1" ), "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))

    backendSystem.shutdown()

    within(20.seconds) {
      awaitAssert {
        frontend ! MalbaProtocol.GetTaskRequest ( "id23", "from1", "taskType1")                                                             
        expectMsg(MalbaProtocol.GetTaskResponse ( "id23", "from1", "taskType1", MalbaProtocol.Ok, Task( "id20", "taskType1", "contents20" ))) 
        frontend ! MalbaProtocol.GetTaskRequest ( "id24", "from1", "taskType1")
        expectMsg(MalbaProtocol.GetTaskResponse ( "id24", "from1", "taskType1", MalbaProtocol.Ok, Task( "id21", "taskType1", "contents21" )))
        frontend ! MalbaProtocol.GetTaskRequest ( "id25", "from1", "taskType1")
        expectMsg(MalbaProtocol.GetTaskResponse ( "id25", "from1", "taskType1", MalbaProtocol.Ok, Task( "id22", "taskType1", "contents22" )))
        frontend ! MalbaProtocol.GetTaskRequest ( "id26", "from1", "taskType1")
        expectMsg(MalbaProtocol.GetNoTaskResponse ( "id26", "from1", "taskType1"))
      }
    }

  }
}
