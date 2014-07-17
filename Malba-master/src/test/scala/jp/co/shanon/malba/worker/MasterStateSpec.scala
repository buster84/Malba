package jp.co.shanon.malba.worker
import org.scalatest._
import org.joda.time.DateTime
import akka.actor.ActorSystem
import akka.serialization._
import scala.collection.immutable.HashMap
import jp.co.shanon.malba.queue.CustomQueue
import jp.co.shanon.malba.queue.FIFOQueue
import jp.co.shanon.malba.queue.BalancingQueue

class MasterStateSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val system = ActorSystem("MasterStateSpec")
  
  import MasterState._
  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination()
  }

  it should "set task type setting" in {
    var state = MasterState.empty
    state = state.setTaskTypeSetting( "taskType1", "aaa" , 1 )
    state should be( MasterState( taskTypeSetting = HashMap( "taskType1" -> "aaa" ), tasks = HashMap.empty[String, CustomQueue]) )
    state = state.setTaskTypeSetting( "taskType2", "bbb" , 1 )
    state should be( MasterState( taskTypeSetting = HashMap( "taskType1" -> "aaa", "taskType2" -> "bbb" ), tasks = HashMap.empty[String, CustomQueue]) )
    state = state.setTaskTypeSetting( "taskType1", "aaa2" , 1 )
    state should be( MasterState( taskTypeSetting = HashMap( "taskType1" -> "aaa2", "taskType2" -> "bbb" ), tasks = HashMap.empty[String, CustomQueue]) )
  }

  it should "enqueue and dequeue task" in {
    var state = MasterState.empty

    val queue = new FIFOQueue()
    queue.enqueue( Task( "id1", "taskType1", "aaa" ), None )
    state = state.enqueue( "id1", "taskType1", "aaa" , None )
    state should be( MasterState( taskTypeSetting = HashMap.empty[String, String], tasks = HashMap( "taskType1" ->  queue) ) )

    val ( task, newState ) = state.dequeue( "taskType1" )
    state = newState
    state should be( MasterState( taskTypeSetting = HashMap.empty[String, String], tasks =  HashMap.empty[String, CustomQueue] ) )
    state.nonEmpty( "taskType1" ) should be( false )
  }
  it should "delete a task by id" in {
    var state = MasterState.empty


    state = state.enqueue( "id1", "taskType1", "aaa1" , None )
    state = state.enqueue( "id2", "taskType1", "aaa2" , None )
    state = state.enqueue( "id3", "taskType1", "aaa3" , None )


    state = state.deleteById( "taskType1", "id2" )
    val queue1 = new FIFOQueue()
    queue1.enqueue( Task( "id1", "taskType1", "aaa1" ), None )
    queue1.enqueue( Task( "id3", "taskType1", "aaa3" ), None )
    state should be( MasterState( taskTypeSetting = HashMap.empty[String, String], tasks = HashMap( "taskType1" ->  queue1) ) )

    state = state.deleteById( "taskType1", "id3" )
    val queue2 = new FIFOQueue()
    queue2.enqueue( Task( "id1", "taskType1", "aaa1" ), None )
    state should be( MasterState( taskTypeSetting = HashMap.empty[String, String], tasks = HashMap( "taskType1" ->  queue2) ) )
    state = state.deleteById( "taskType1", "id1" )
    state should be( MasterState( taskTypeSetting = HashMap.empty[String, String], tasks = HashMap.empty[String, CustomQueue] ) )
  }

  it should "delete tasks by group" in {
    var state = MasterState.empty

    state = state.enqueue( "id1", "taskType1", "aaa1" , Some( "hoge" ) )
    state = state.enqueue( "id2", "taskType1", "aaa2" , Some( "hoge" ) )
    state = state.enqueue( "id3", "taskType1", "aaa3" , Some( "hoge" ) )


    state = state.deleteByGroup( "taskType1", "hoge" )
    state should be( MasterState( taskTypeSetting = HashMap.empty[String, String], tasks = HashMap.empty[String, CustomQueue] ) )
  }

  it should "use custom queue of setting" in {
    var state = MasterState.empty

    state = state.setTaskTypeSetting( "taskType1", "jp.co.shanon.malba.queue.BalancingQueue", 0 )
    state = state.enqueue( "id1", "taskType1", "aaa1" , None )
    val queue1 = new BalancingQueue()
    queue1.enqueue( Task( "id1", "taskType1", "aaa1" ), None )
    state should be( MasterState( taskTypeSetting = HashMap( "taskType1" -> "jp.co.shanon.malba.queue.BalancingQueue" ), tasks = HashMap( "taskType1" ->  queue1) ) )
  }


  it should "be serializable with BalancingQueue" in {
    var state = MasterState.empty

    state = state.setTaskTypeSetting( "taskType1", "jp.co.shanon.malba.queue.BalancingQueue", 0 )
    state = state.enqueue( "id1", "taskType1", "aaa1" , None )

    val serialization = SerializationExtension(system)
    val serializer = serialization.findSerializerFor(state)
    val bytes      = serializer.toBinary(state)
    val back       = serializer.fromBinary(bytes, manifest = None)
    back should be(state)
  }

  it should "be serializable with FIFOQueue" in {
    var state = MasterState.empty

    state = state.setTaskTypeSetting( "taskType1", "jp.co.shanon.malba.queue.FIFOQueue", 0 )
    state = state.enqueue( "id1", "taskType1", "aaa1" , None )

    val serialization = SerializationExtension(system)
    val serializer = serialization.findSerializerFor(state)
    val bytes      = serializer.toBinary(state)
    val back       = serializer.fromBinary(bytes, manifest = None)
    back should be(state)
  }
}
