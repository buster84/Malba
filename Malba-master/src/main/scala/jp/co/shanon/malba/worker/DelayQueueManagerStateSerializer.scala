package jp.co.shanon.malba.worker
import akka.serialization._
import MalbaProtocol.AddDelayTaskRequest
 
/**
 * This serializer is for scala-2.10.x. There was a bug in serialization of PriorityQueue.
 * see https://issues.scala-lang.org/browse/SI-7568
 */
class DelayQueueManagerStateSerializer( system: akka.actor.ExtendedActorSystem ) extends Serializer {
  val javaSerializer = new JavaSerializer( system )

  // This is whether "fromBinary" requires a "clazz" or not
  def includeManifest: Boolean = false
 
  // Pick a unique identifier for your Serializer,
  // you've got a couple of billions to choose from,
  // 0 - 16 is reserved by Akka itself
  def identifier = 1000
 
  // "toBinary" serializes the given object to an Array of Bytes
  def toBinary(obj: AnyRef): Array[Byte] = {
    obj match {
      case state: DelayQueueManagerState =>
        javaSerializer.toBinary(state.addDelayTaskRequestList.toArray) 
      case _ => throw new RuntimeException( obj.getClass.getName + " can not serialize by QueueManagerStateSerializer" )
    }
  }
 
  // "fromBinary" deserializes the given array,
  // using the type hint (if any, see "includeManifest" above)
  // into the optionally provided classLoader.
  def fromBinary(bytes: Array[Byte],
                 clazz: Option[Class[_]]): AnyRef = {

    val state = DelayQueueManagerState.empty
    javaSerializer.fromBinary(bytes).asInstanceOf[Array[AddDelayTaskRequest]].foreach{ req =>
      state.enqueueTaskRequest( req )
    }
    state
  }
}
