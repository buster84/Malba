package jp.co.shanon.malba.worker
import org.scalatest._
import org.joda.time.DateTime
import akka.actor.ActorSystem
import akka.serialization._
import scala.collection.immutable.HashMap

class WorkerStateSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val system = ActorSystem("WorkStateSpec")
  
  import WorkerState._
  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination()
  }

  it should "be updated by events" in {
    val now   = DateTime.now()
    var state = WorkerState.empty
    state = state.updated(WorkerAdded("path1", "type1"))
    state = state.updated(WorkerAdded("path2", "type1"))
    state = state.updated(WorkerAdded("path3", "type1"))
    state = state.updated(WorkerAdded("path4", "type2"))
    state = state.updated(WorkerAdded("path5", "type2"))
    state = state.updated(WorkerAdded("path6", "type3"))

    state should be (
      WorkerState(
        HashMap("type1" -> Seq("path1", "path2", "path3"), "type2" -> Seq("path4", "path5"), "type3" -> Seq("path6"))
    ))

    // Kill
    state = state.updated(WorkerKilled("path1", "type1"))
    state = state.updated(WorkerKilled("path4", "type2"))
    state = state.updated(WorkerKilled("path5", "type2"))
    state should be (
      WorkerState(
        HashMap("type1" -> Seq("path2", "path3"), "type3" -> Seq("path6"))
    ))
  }

  it should "be serializable" in {
    var state = WorkerState.empty
    state = state.updated(WorkerAdded("path1", "type1"))
    state = state.updated(WorkerAdded("path2", "type1"))
    state = state.updated(WorkerAdded("path3", "type1"))
    state = state.updated(WorkerAdded("path4", "type2"))
    state = state.updated(WorkerAdded("path5", "type2"))
    state = state.updated(WorkerAdded("path6", "type3"))
    state = state.updated(WorkerKilled("path1", "type1"))
    state = state.updated(WorkerKilled("path4", "type2"))
    state = state.updated(WorkerKilled("path5", "type2"))

    val serialization = SerializationExtension(system)
    val serializer = serialization.findSerializerFor(state)
    val bytes      = serializer.toBinary(state)
    val back       = serializer.fromBinary(bytes, manifest = None)
    back should be(state)
  }
}
