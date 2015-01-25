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
import org.joda.time.DateTime

object MasterSpec {

  val clusterConfig = ConfigFactory.parseString("""
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.actor.serializers.delay-queue-state = "jp.co.shanon.malba.worker.DelayQueueManagerStateSerializer"
    akka.actor.serialization-bindings = {
      "jp.co.shanon.malba.worker.DelayQueueManagerState" = delay-queue-state
    }
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



  def withSystems(testCode: (ActorSystem, ActorSystem, ActorSystem) => Any) {
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
    try {
      storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
      testCode(taskExecuterSystem, backendSystem, backendSystem2) 
      ()
    } finally {
      if( !backendSystem.isTerminated  ) {
        backendSystem.shutdown()
        backendSystem.awaitTermination()
      }
      if( !backendSystem2.isTerminated  ) {
        backendSystem2.shutdown()
        backendSystem2.awaitTermination()
      }
      if( !taskExecuterSystem.isTerminated ) {
        taskExecuterSystem.shutdown()
        taskExecuterSystem.awaitTermination()
      }
      storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
    }
  }

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))

  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination()
    storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
  }

  "Master" should "work as queuing server" in withSystems { ( taskExecuterSystem, backendSystem, backendSystem2 ) =>

    val singletonManager1 = backendSystem.actorOf(ClusterSingletonManager.props(Props( classOf[Master], "workerManager1", Duration(100, MILLISECONDS), Duration(5000, MILLISECONDS) ), "active",
      PoisonPill, Some("backend"), 5, 4), "master")
    val singletonManager2 = backendSystem2.actorOf(ClusterSingletonManager.props(Props( classOf[Master], "workerManager1", Duration(100, MILLISECONDS), Duration(5000, MILLISECONDS) ), "active",
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
    Thread.sleep(1000)

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

  "Master" should "work as delay queuing server" in withSystems { ( taskExecuterSystem, backendSystem, backendSystem2 ) =>

    val singletonManager1 = backendSystem.actorOf(ClusterSingletonManager.props(Props( classOf[Master], "workerManager1", Duration(100, MILLISECONDS), Duration(5000, MILLISECONDS) ), "active",
      PoisonPill, Some("backend"), 5, 4), "master")
    val singletonManager2 = backendSystem2.actorOf(ClusterSingletonManager.props(Props( classOf[Master], "workerManager1", Duration(100, MILLISECONDS), Duration(5000, MILLISECONDS) ), "active",
      PoisonPill, Some("backend"), 5, 4), "master")

    val frontend = system.actorOf(Props[Frontend], "frontend1")

    val taskExecuter  = taskExecuterSystem.actorOf(Props(classOf[MasterSpec.WorkExecutor], testProbe.ref), "taskExecuter1")
    val actorPath     = MasterSpec.actorPath + "1"

    // Add delay task
    within(20.seconds) {
      awaitAssert {
        frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id1", taskId = "id1", from = "from1", group = None, 
          option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusSeconds(10) )
        expectMsg(MalbaProtocol.AddTaskResponse ( "id1", "from1", "id1", None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
        frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id1", taskId = "id1", from = "from1", group = None, 
          option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusSeconds(11) )
        expectMsg(MalbaProtocol.AddTaskResponse ( "id1", "from1", "id1",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))

        frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id2", taskId = "id2", from = "from1", group = None, 
          option = Map.empty[String, String], taskType = "taskType1", task = "task2", scheduledTime = DateTime.now.plusSeconds(12) )
        expectMsg(MalbaProtocol.AddTaskResponse ( "id2", "from1", "id2",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))

        frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id3", taskId = "id3", from = "from1", group = None, 
          option = Map.empty[String, String], taskType = "taskType1", task = "task3", scheduledTime = DateTime.now.plusSeconds(13) )
        expectMsg(MalbaProtocol.AddTaskResponse ( "id3", "from1", "id3",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
      }
    }

    // Get task
    frontend ! MalbaProtocol.GetTaskRequest( "id4", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetNoTaskResponse( "id4", "from1", "taskType1"))

    Thread.sleep(20000)
    frontend ! MalbaProtocol.GetTaskRequest( "id5", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetTaskResponse( "id5", "from1", "taskType1", MalbaProtocol.Ok, Task( "id1", "taskType1", "task1" )))
    frontend ! MalbaProtocol.GetTaskRequest( "id6", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetTaskResponse( "id6", "from1", "taskType1", MalbaProtocol.Ok, Task( "id2", "taskType1", "task2" )))
    frontend ! MalbaProtocol.GetTaskRequest( "id7", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetTaskResponse( "id7", "from1", "taskType1", MalbaProtocol.Ok, Task( "id3", "taskType1", "task3" )))


    // Cancel by id
    frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id8", taskId = "id8", from = "from1", group = None, 
      option = Map.empty[String, String], taskType = "taskType1", task = "task8", scheduledTime = DateTime.now.plusSeconds(10) )
    expectMsg(MalbaProtocol.AddTaskResponse ( "id8", "from1", "id8", None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id9", taskId = "id9", from = "from1", group = None, 
      option = Map.empty[String, String], taskType = "taskType1", task = "task9", scheduledTime = DateTime.now.plusSeconds(12) )
    expectMsg(MalbaProtocol.AddTaskResponse ( "id9", "from1", "id9",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id10", taskId = "id10", from = "from1", group = None, 
      option = Map.empty[String, String], taskType = "taskType1", task = "task10", scheduledTime = DateTime.now.plusSeconds(13) )
    expectMsg(MalbaProtocol.AddTaskResponse ( "id10", "from1", "id10",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))

    frontend ! MalbaProtocol.CancelDelayTaskByIdRequest( "id11", "from1", "taskType1", "id9" )
    expectMsg(MalbaProtocol.CancelTaskByIdResponse ( "id11", "from1", "taskType1", "id9", MalbaProtocol.Ok ))

    Thread.sleep(20000)
    frontend ! MalbaProtocol.GetTaskRequest( "id12", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetTaskResponse( "id12", "from1", "taskType1", MalbaProtocol.Ok, Task( "id8", "taskType1", "task8" )))
    frontend ! MalbaProtocol.GetTaskRequest( "id13", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetTaskResponse( "id13", "from1", "taskType1", MalbaProtocol.Ok, Task( "id10", "taskType1", "task10" )))

    frontend ! MalbaProtocol.GetTaskRequest( "id14", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetNoTaskResponse( "id14", "from1", "taskType1"))

    // Cancel by group
    frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id15", taskId = "id15", from = "from1", group = Some( "group" ), 
      option = Map.empty[String, String], taskType = "taskType1", task = "task15", scheduledTime = DateTime.now.plusSeconds(10) )
    expectMsg(MalbaProtocol.AddTaskResponse ( "id15", "from1", "id15", Some( "group" ), "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id16", taskId = "id16", from = "from1", group = Some( "group" ), 
      option = Map.empty[String, String], taskType = "taskType1", task = "task16", scheduledTime = DateTime.now.plusSeconds(12) )
    expectMsg(MalbaProtocol.AddTaskResponse ( "id16", "from1", "id16", Some( "group" ), "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id17", taskId = "id17", from = "from1", group = None, 
      option = Map.empty[String, String], taskType = "taskType1", task = "task17", scheduledTime = DateTime.now.plusSeconds(13) )
    expectMsg(MalbaProtocol.AddTaskResponse ( "id17", "from1", "id17",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))

    frontend ! MalbaProtocol.CancelDelayTaskByGroupRequest( "id18", "from1", "taskType1", "group" )
    expectMsg(MalbaProtocol.CancelTaskByGroupResponse( "id18", "from1", "taskType1", "group", MalbaProtocol.Ok ))

    Thread.sleep(20000)
    frontend ! MalbaProtocol.GetTaskRequest( "id19", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetTaskResponse( "id19", "from1", "taskType1", MalbaProtocol.Ok, Task( "id17", "taskType1", "task17" )))

    frontend ! MalbaProtocol.GetTaskRequest( "id20", "from1", "taskType1")
    expectMsg(MalbaProtocol.GetNoTaskResponse( "id20", "from1", "taskType1"))


    // Check persistence
    frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id21", taskId = "id21", from = "from1", group = None, 
      option = Map.empty[String, String], taskType = "taskType1", task = "task21", scheduledTime = DateTime.now.plusSeconds(10) )
    expectMsg(MalbaProtocol.AddTaskResponse ( "id21", "from1", "id21", None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id22", taskId = "id22", from = "from1", group = None, 
      option = Map.empty[String, String], taskType = "taskType1", task = "task22", scheduledTime = DateTime.now.plusSeconds(12) )
    expectMsg(MalbaProtocol.AddTaskResponse ( "id22", "from1", "id22",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    frontend ! MalbaProtocol.AddDelayTaskRequest( id = "id23", taskId = "id23", from = "from1", group = None, 
      option = Map.empty[String, String], taskType = "taskType1", task = "task23", scheduledTime = DateTime.now.plusSeconds(13) )
    expectMsg(MalbaProtocol.AddTaskResponse ( "id23", "from1", "id23",None, "taskType1", MalbaProtocol.Ok, 0L, 0L, 0L))
    Thread.sleep(1000)

    backendSystem.shutdown()

    Thread.sleep(20000)
    within(20.seconds) {
      awaitAssert {
        frontend ! MalbaProtocol.GetTaskRequest( "id24", "from1", "taskType1")
        expectMsg(MalbaProtocol.GetTaskResponse( "id24", "from1", "taskType1", MalbaProtocol.Ok, Task( "id21", "taskType1", "task21" )))
        frontend ! MalbaProtocol.GetTaskRequest( "id25", "from1", "taskType1")
        expectMsg(MalbaProtocol.GetTaskResponse( "id25", "from1", "taskType1", MalbaProtocol.Ok, Task( "id22", "taskType1", "task22" )))
        frontend ! MalbaProtocol.GetTaskRequest( "id26", "from1", "taskType1")
        expectMsg(MalbaProtocol.GetTaskResponse( "id26", "from1", "taskType1", MalbaProtocol.Ok, Task( "id23", "taskType1", "task23" )))
      }
    }

  }
}
