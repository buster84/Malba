package jp.co.shanon.malba.worker
import org.scalatest._
import org.joda.time.DateTime
import akka.actor.ActorSystem
import akka.serialization._
import scala.collection.immutable.HashMap
import jp.co.shanon.malba.queue.CustomQueue
import jp.co.shanon.malba.queue.FIFOQueue
import jp.co.shanon.malba.queue.BalancingQueue

import MalbaProtocol.AddDelayTaskRequest

class DelayQueueManagerStateSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val system = ActorSystem("DelayQueueManagerStateSpec")
  
  import DelayQueueManagerState._
  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination()
  }

  "existsEqueueableTask" should "check whether or not there is an enqueable task" in {
    var state = DelayQueueManagerState.empty
    state.existsEqueueableTask() should be(false)
    
    state = state.enqueueTaskRequest( AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( 1 ) ) )
    state.existsEqueueableTask() should be(false)

    state = state.enqueueTaskRequest( AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -1 ) ) )
    state.existsEqueueableTask() should be(true)
  }

  "dequeueTaskRequest" should "dequeue AddDelayTaskRequest sorted by scheduledTime" in {
    val taskReq1 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( 1 ) )
    val taskReq2 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -2 ) )
    val taskReq3 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -1 ) )
    var state = DelayQueueManagerState.empty
    state = state.enqueueTaskRequest(taskReq1)
    state = state.enqueueTaskRequest(taskReq2)
    state = state.enqueueTaskRequest(taskReq3)
    val ( task1, _ ) = state.dequeueTaskRequest()
    val ( task2, _ ) = state.dequeueTaskRequest()
    val ( task3, _ ) = state.dequeueTaskRequest()
    task1 should be ( taskReq2 )
    task2 should be ( taskReq3 )
    task3 should be ( taskReq1 )
    state.existsEqueueableTask() should be(false)
  }

  "takeAllEnqueueableTasks" should "dequeue AddDelayTaskRequest list sorted by scheduledTime" in {
    val taskReq1 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( 1 ) )
    val taskReq2 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -2 ) )
    val taskReq3 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -1 ) )
    var state = DelayQueueManagerState.empty
    state = state.enqueueTaskRequest(taskReq1)
    state = state.enqueueTaskRequest(taskReq2)
    state = state.enqueueTaskRequest(taskReq3)
    val ( taskList, _ ) = state.takeAllEnqueueableTasks( DateTime.now )
    taskList should be ( List( taskReq2, taskReq3 ) )
    state.existsEqueueableTask() should be(false)
  }

  "enqueueTaskRequest" should "enqueue AddDelayTaskRequest" in {
    val taskReq1 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -1 ) )
    var state = DelayQueueManagerState.empty
    state = state.enqueueTaskRequest(taskReq1)
    state.existsEqueueableTask() should be(true)
  }

  "contains" should "check whether or not there is same AddDelayTaskRequest as given taskType and taskId" in {
    val taskReq1 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -1 ) )
    var state = DelayQueueManagerState.empty
    state.contains(taskType = "taskType1", taskId = "taskId1") should be(false)

    state = state.enqueueTaskRequest(taskReq1)
    state.contains(taskType = "taskType1", taskId = "taskId1") should be(true)
  }

  "deleteTaskById" should "delete AddDelayTaskRequest by given taskType and taskId" in {
    val taskReq1 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( 1 ) )
    val taskReq2 = AddDelayTaskRequest( id = "id1", taskId = "taskId2", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -2 ) )
    val taskReq3 = AddDelayTaskRequest( id = "id1", taskId = "taskId3", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -1 ) )
    var state = DelayQueueManagerState.empty
    state = state.enqueueTaskRequest(taskReq1)
    state = state.enqueueTaskRequest(taskReq2)
    state = state.enqueueTaskRequest(taskReq3)
    state.contains(taskType = "taskType1", taskId = "taskId3") should be(true)
    state = state.deleteTaskById( taskType = "taskType1", taskId = "taskId3" )
    state.contains(taskType = "taskType1", taskId = "taskId3") should be(false)
  }

  "deleteTaskByGroup" should "delete taskRequests by group" in {
    val taskReq1 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = Some( "group" ),
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( 1 ) )
    val taskReq2 = AddDelayTaskRequest( id = "id1", taskId = "taskId2", from = "from1", group = Some( "group" ),
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -2 ) )
    val taskReq3 = AddDelayTaskRequest( id = "id1", taskId = "taskId3", from = "from1", group = Some( "group" ),
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -1 ) )
    var state = DelayQueueManagerState.empty
    state = state.enqueueTaskRequest(taskReq1)
    state = state.enqueueTaskRequest(taskReq2)
    state = state.enqueueTaskRequest(taskReq3)
    state.contains(taskType = "taskType1", taskId = "taskId1") should be(true)
    state.contains(taskType = "taskType1", taskId = "taskId2") should be(true)
    state.contains(taskType = "taskType1", taskId = "taskId3") should be(true)
    state = state.deleteTaskByGroup( taskType = "taskType1", group = "group" )
    state.contains(taskType = "taskType1", taskId = "taskId1") should be(false)
    state.contains(taskType = "taskType1", taskId = "taskId2") should be(false)
    state.contains(taskType = "taskType1", taskId = "taskId3") should be(false)
  }

  "updated" should "return updated state by a given event" in {
    val taskReq1 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = Some( "group" ),
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( 1 ) )
    val taskReq2 = AddDelayTaskRequest( id = "id1", taskId = "taskId2", from = "from1", group = Some( "group" ),
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -2 ) )
    val taskReq3 = AddDelayTaskRequest( id = "id1", taskId = "taskId3", from = "from1", group = Some( "group" ),
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -1 ) )

    var state        = DelayQueueManagerState.empty
    state = state.updated(DelayTaskAdded(taskReq1))
    state = state.updated(DelayTaskAdded(taskReq2))
    state = state.updated(DelayTaskAdded(taskReq3))
    state.contains(taskType = "taskType1", taskId = "taskId1") should be(true)
    state.contains(taskType = "taskType1", taskId = "taskId2") should be(true)
    state.contains(taskType = "taskType1", taskId = "taskId3") should be(true)

    state = state.updated(DelayTasksSent(DateTime.now))
    state.contains(taskType = "taskType1", taskId = "taskId1") should be(true)
    state.contains(taskType = "taskType1", taskId = "taskId2") should be(false)
    state.contains(taskType = "taskType1", taskId = "taskId3") should be(false)

    state = state.updated(DelayTaskCanceledById(id = "id1", from = "from1", taskType = "taskType1", taskId = "taskId1"))
    state.contains(taskType = "taskType1", taskId = "taskId1") should be(false)

    state = state.updated(DelayTaskAdded(taskReq1))
    state = state.updated(DelayTaskAdded(taskReq2))
    state = state.updated(DelayTaskAdded(taskReq3))
    state = state.updated(DelayTaskCanceledByGroup(id = "id1", from = "from1", taskType = "taskType1", group = "group"))
  }

  "DelayQueueManagerState" should "be serializable" in {
    val taskReq1 = AddDelayTaskRequest( id = "id1", taskId = "taskId1", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( 1 ) )
    val taskReq2 = AddDelayTaskRequest( id = "id1", taskId = "taskId2", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -2 ) )
    val taskReq3 = AddDelayTaskRequest( id = "id1", taskId = "taskId3", from = "from1", group = None,
      option = Map.empty[String, String], taskType = "taskType1", task = "task1", scheduledTime = DateTime.now.plusDays( -1 ) )
    var state = DelayQueueManagerState.empty
    state = state.enqueueTaskRequest(taskReq1)
    state = state.enqueueTaskRequest(taskReq2)
    state = state.enqueueTaskRequest(taskReq3)


    val serialization = SerializationExtension(system)
    val serializer    = serialization.findSerializerFor(state)
    val bytes         = serializer.toBinary(state)
    val back          = serializer.fromBinary(bytes, manifest = None).asInstanceOf[DelayQueueManagerState]
    back.contains(taskType = "taskType1", taskId = "taskId1") should be(true)
    back.contains(taskType = "taskType1", taskId = "taskId2") should be(true)
    back.contains(taskType = "taskType1", taskId = "taskId3") should be(true)
  }

}
