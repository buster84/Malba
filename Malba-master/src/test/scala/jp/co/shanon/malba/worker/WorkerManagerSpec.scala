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

object WorkerManagerSpec {

  val workerManagerConfig = ConfigFactory.parseString("""
    akka.actor.provider = "akka.remote.RemoteActorRefProvider"
    akka.remote.netty.tcp.port=2551
    akka.remote.netty.tcp.hostname=localhost
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
    akka.remote.netty.tcp.port=2552
    """)

  val actorPath: String = "akka.tcp://TaskExecuterSpec@localhost:2552/user/taskExecuter"
  val taskType: String  = "testTaskType"
  var requestid         = "id1"
  val from              = "from"

  class WorkExecutor(ref: ActorRef) extends Actor {
    def receive = {
      case WorkerProtocol.GetState =>
        sender() ! MalbaProtocol.Unknown("hoge")
      case WorkerProtocol.TaskIsReady =>
        sender() ! MalbaProtocol.GetTaskRequest(requestid, from, taskType)
      case m: MalbaProtocol.GetTaskResponse =>
        ref ! "task"
      case m: MalbaProtocol.GetNoTaskResponse =>
        ref ! "notask"
    }
  }
}

class WorkerManagerSpec(var _system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll
  with ImplicitSender {

  def this() = this(ActorSystem("WorkerManagerSpec", WorkerManagerSpec.workerManagerConfig))

  val testProbe = TestProbe()


  val taskExecuterSystem: ActorSystem = ActorSystem("TaskExecuterSpec", WorkerManagerSpec.taskExecuterConfig)

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))


  override def beforeAll: Unit = {
    storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
  }

  override def afterAll: Unit = {
    system.shutdown()
    taskExecuterSystem.shutdown()
    system.awaitTermination()
    taskExecuterSystem.awaitTermination()

    storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
  }

  "WorkerManager" should "add worker" in {
    val master        = TestProbe()
    val workerManager = system.actorOf(Props(classOf[WorkerManager], "workerManager1", master.ref), "workerManager1")
    val taskExecuter  = taskExecuterSystem.actorOf(Props(classOf[WorkerManagerSpec.WorkExecutor], testProbe.ref), "taskExecuter1")
    val actorPath     = WorkerManagerSpec.actorPath + "1"

    within(10.seconds) {
      awaitAssert {
        workerManager ! MalbaProtocol.AddWorkerRequest( "id1", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id1", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Ok ))
        workerManager ! MalbaProtocol.AddWorkerRequest( "id1", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id1", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Ok ))
      }
    }
  }
  it should "reject adding duplicated worker" in {
    val master        = TestProbe()
    val workerManager = system.actorOf(Props(classOf[WorkerManager], "workerManager2", master.ref), "workerManager2")
    val taskExecuter  = taskExecuterSystem.actorOf(Props(classOf[WorkerManagerSpec.WorkExecutor], testProbe.ref), "taskExecuter2")
    val actorPath     = WorkerManagerSpec.actorPath + "2"

    within(10.seconds) {
      awaitAssert {
        workerManager ! MalbaProtocol.AddWorkerRequest( "id1", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id1", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Ok ))
        workerManager ! MalbaProtocol.AddWorkerRequest( "id2", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id2", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Reject("400", s"There is already ${actorPath} worker.") ))
      }
    }
  }
  it should "recover states" in {
    val master        = TestProbe()
    val workerManager = system.actorOf(Props(classOf[WorkerManager], "workerManager3", master.ref), "workerManager3")
    val taskExecuter  = taskExecuterSystem.actorOf(Props(classOf[WorkerManagerSpec.WorkExecutor], testProbe.ref), "taskExecuter3")
    val actorPath     = WorkerManagerSpec.actorPath + "3"
    within(10.seconds) {
      awaitAssert {
        workerManager ! MalbaProtocol.AddWorkerRequest( "id1", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id1", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Ok ))
      }
    }
    workerManager ! PoisonPill
    Thread.sleep(5000)
    val workerManager2 = system.actorOf(Props(classOf[WorkerManager], "workerManager3", master.ref), "workerManager3")
    within(10.seconds) {
      awaitAssert {
        workerManager2 ! MalbaProtocol.AddWorkerRequest( "id2", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id2", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Reject("400", s"There is already ${actorPath} worker.") ))
      }
    }
  }

  it should "send notification" in {
    val master        = TestProbe()
    val workerManager = system.actorOf(Props(classOf[WorkerManager], "workerManager4", master.ref), "workerManager4")
    val taskExecuter  = taskExecuterSystem.actorOf(Props(classOf[WorkerManagerSpec.WorkExecutor], testProbe.ref), "taskExecuter4")
    val actorPath     = WorkerManagerSpec.actorPath + "4"

    within(10.seconds) {
      awaitAssert {
        workerManager ! MalbaProtocol.AddWorkerRequest( "id1", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id1", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Ok ))
        workerManager ! MasterProtocol.Notify( WorkerManagerSpec.taskType )
        val taskRequest = master.expectMsgType[MalbaProtocol.GetTaskRequest]
        taskRequest.taskType should be(WorkerManagerSpec.taskType)
      }
    }
  }

  it should "remove worker when the worker is terminated" in {
    val master        = TestProbe()
    val workerManager = system.actorOf(Props(classOf[WorkerManager], "workerManager5", master.ref), "workerManager5")
    val taskExecuter  = taskExecuterSystem.actorOf(Props(classOf[WorkerManagerSpec.WorkExecutor], testProbe.ref), "taskExecuter5")
    val actorPath     = WorkerManagerSpec.actorPath + "5"

    within(10.seconds) {
      awaitAssert {
        workerManager ! MalbaProtocol.AddWorkerRequest( "id1", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id1", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Ok ))
        workerManager ! MalbaProtocol.AddWorkerRequest( "id2", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id2", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Reject("400", s"There is already ${actorPath} worker.") ))
      }
    }
    taskExecuter ! PoisonPill
    Thread.sleep( 3000 )
    taskExecuterSystem.actorOf(Props(classOf[WorkerManagerSpec.WorkExecutor], testProbe.ref), "taskExecuter5")
    within(10.seconds) {
      awaitAssert {
        workerManager ! MalbaProtocol.AddWorkerRequest( "id3", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id3", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Ok ))
      }
    }
  }

  it should "return worker state" in {
    val master        = TestProbe()
    val workerManager = system.actorOf(Props(classOf[WorkerManager], "workerManager6", master.ref), "workerManager6")
    val taskExecuter  = taskExecuterSystem.actorOf(Props(classOf[WorkerManagerSpec.WorkExecutor], testProbe.ref), "taskExecuter6")
    val actorPath     = WorkerManagerSpec.actorPath + "6"

    within(10.seconds) {
      awaitAssert {
        workerManager ! MalbaProtocol.AddWorkerRequest( "id4", WorkerManagerSpec.taskType, actorPath )
        expectMsg(MalbaProtocol.AddWorkerResponse ( "id4", WorkerManagerSpec.taskType, actorPath, MalbaProtocol.Ok ))
        workerManager ! MalbaProtocol.GetWorkerStateRequest( WorkerManagerSpec.taskType )
        expectMsg(MalbaProtocol.GetWorkerStateResponse ( WorkerManagerSpec.taskType, Seq(MalbaProtocol.Unknown("hoge"))))
      }
    }
  }
}
