package jp.co.shanon.malba.worker
import org.scalatest._
import org.joda.time.DateTime
import akka.actor.ActorSystem
import akka.serialization._
import scala.collection.immutable.HashMap
import jp.co.shanon.malba.queue.CustomQueue
import jp.co.shanon.malba.queue.FIFOQueue
import jp.co.shanon.malba.queue.BalancingQueue

class QueueManagerStateSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val system = ActorSystem("QueueManagerStateSpec")
  
  import QueueManagerState._
  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination()
  }

  it should "set task type setting" in {
    var state = QueueManagerState.empty
    state = state.setTaskTypeSetting( "taskType1", "aaa" , 1 , Map.empty[String, String])
    state should be( QueueManagerState( taskTypeSetting = HashMap( "taskType1" -> Tuple2( "aaa", Map.empty[String, String] ) ), tasks = HashMap.empty[String, CustomQueue]) )
    state = state.setTaskTypeSetting( "taskType2", "bbb" , 1 , Map.empty[String, String])
    state should be( QueueManagerState( taskTypeSetting = HashMap( "taskType1" -> Tuple2( "aaa", Map.empty[String, String] ), "taskType2" -> Tuple2( "bbb", Map.empty[String, String] ) ), tasks = HashMap.empty[String, CustomQueue]) )
    state = state.setTaskTypeSetting( "taskType1", "aaa2" , 1 , Map.empty[String, String])
    state should be( QueueManagerState( taskTypeSetting = HashMap( "taskType1" -> Tuple2( "aaa2", Map.empty[String, String] ), "taskType2" -> Tuple2( "bbb", Map.empty[String, String] ) ), tasks = HashMap.empty[String, CustomQueue]) )
  }

  it should "enqueue and dequeue task" in {
    var state = QueueManagerState.empty

    val queue = new FIFOQueue()
    queue.enqueue( Task( "id1", "taskType1", "aaa" ), None, Map.empty[String, String] )
    state = state.enqueue( "id1", "taskType1", "aaa" , None, Map.empty[String, String] )
    state should be( QueueManagerState( taskTypeSetting = HashMap.empty[String, ( String, Map[String, String] )], tasks = HashMap( "taskType1" ->  queue) ) )

    val ( task, newState ) = state.dequeue( "taskType1" )
    state = newState
    state should be( QueueManagerState( taskTypeSetting = HashMap.empty[String, ( String, Map[String, String] )], tasks =  HashMap.empty[String, CustomQueue] ) )
    state.nonEmpty( "taskType1" ) should be( false )
  }
  it should "delete a task by id" in {
    var state = QueueManagerState.empty


    state = state.enqueue( "id1", "taskType1", "aaa1" , None, Map.empty[String, String] )
    state = state.enqueue( "id2", "taskType1", "aaa2" , None, Map.empty[String, String] )
    state = state.enqueue( "id3", "taskType1", "aaa3" , None, Map.empty[String, String] )


    state = state.deleteById( "taskType1", "id2" )
    val queue1 = new FIFOQueue()
    queue1.enqueue( Task( "id1", "taskType1", "aaa1" ), None, Map.empty[String, String] )
    queue1.enqueue( Task( "id3", "taskType1", "aaa3" ), None, Map.empty[String, String] )
    state should be( QueueManagerState( taskTypeSetting = HashMap.empty[String, ( String,  Map[String, String])], tasks = HashMap( "taskType1" ->  queue1) ) )

    state = state.deleteById( "taskType1", "id3" )
    val queue2 = new FIFOQueue()
    queue2.enqueue( Task( "id1", "taskType1", "aaa1" ), None, Map.empty[String, String] )
    state should be( QueueManagerState( taskTypeSetting = HashMap.empty[String, ( String, Map[String, String])], tasks = HashMap( "taskType1" ->  queue2) ) )
    state = state.deleteById( "taskType1", "id1" )
    state should be( QueueManagerState( taskTypeSetting = HashMap.empty[String, ( String, Map[String, String] )], tasks = HashMap.empty[String, CustomQueue] ) )
  }

  it should "delete tasks by group" in {
    var state = QueueManagerState.empty

    state = state.enqueue( "id1", "taskType1", "aaa1" , Some( "hoge" ), Map.empty[String, String] )
    state = state.enqueue( "id2", "taskType1", "aaa2" , Some( "hoge" ), Map.empty[String, String] )
    state = state.enqueue( "id3", "taskType1", "aaa3" , Some( "hoge" ), Map.empty[String, String] )


    state = state.deleteByGroup( "taskType1", "hoge" )
    state should be( QueueManagerState( taskTypeSetting = HashMap.empty[String, ( String, Map[String, String] )], tasks = HashMap.empty[String, CustomQueue] ) )
  }

  it should "use custom queue of setting" in {
    var state = QueueManagerState.empty

    state = state.setTaskTypeSetting( "taskType1", "jp.co.shanon.malba.queue.BalancingQueue", 0 , Map.empty[String, String])
    state = state.enqueue( "id1", "taskType1", "aaa1" , None, Map.empty[String, String] )
    val queue1 = new BalancingQueue()
    queue1.enqueue( Task( "id1", "taskType1", "aaa1" ), None, Map.empty[String, String] )
    state should be( QueueManagerState( taskTypeSetting = HashMap( "taskType1" -> Tuple2("jp.co.shanon.malba.queue.BalancingQueue", Map.empty[String, String]) ), tasks = HashMap( "taskType1" ->  queue1) ) )
  }


  it should "be serializable with BalancingQueue" in {
    var state = QueueManagerState.empty

    state = state.setTaskTypeSetting( "taskType1", "jp.co.shanon.malba.queue.BalancingQueue", 0 , Map.empty[String, String])
    state = state.enqueue( "id1", "taskType1", "aaa1" , None, Map.empty[String, String] )

    val serialization = SerializationExtension(system)
    val serializer = serialization.findSerializerFor(state)
    val bytes      = serializer.toBinary(state)
    val back       = serializer.fromBinary(bytes, manifest = None)
    back should be(state)
  }

  it should "be serializable with FIFOQueue" in {
    var state = QueueManagerState.empty

    state = state.setTaskTypeSetting( "taskType1", "jp.co.shanon.malba.queue.FIFOQueue", 0 , Map.empty[String, String])
    state = state.enqueue( "id1", "taskType1", "aaa1" , None, Map.empty[String, String] )

    val serialization = SerializationExtension(system)
    val serializer = serialization.findSerializerFor(state)
    val bytes      = serializer.toBinary(state)
    val back       = serializer.fromBinary(bytes, manifest = None)
    back should be(state)
  }
}
