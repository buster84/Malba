package jp.co.shanon.malba.queue
import org.scalatest._
import org.joda.time.DateTime
import akka.actor.ActorSystem
import akka.serialization._
import scala.collection.immutable.HashMap
import jp.co.shanon.malba.worker.Task

class BalancingQueueSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val system = ActorSystem("BalancingQueueSpec")
  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination()
  }

  "BalancingQueue" should "check if empty or not" in {
    val queue = new BalancingQueue()
    queue.isEmpty should be(true)
    queue.enqueue(Task("id1", "taskType1", "content1"), None)
    queue.isEmpty should be(false)
    queue.dequeue()
    queue.isEmpty should be(true)
  }
  it should "perform FIFO in same group" in {
    val queue = new BalancingQueue()
    val task1 = Task("id1", "taskType1", "content1")
    val task2 = Task("id2", "taskType1", "content2")
    val task3 = Task("id3", "taskType1", "content3")
    val task4 = Task("id4", "taskType1", "content4")
    queue.enqueue(task1, Some( "hoge" ))
    queue.enqueue(task2, Some( "hoge" ))
    queue.enqueue(task3, Some( "hoge" ))
    queue.enqueue(task4, Some( "hoge" ))
    queue.dequeue() should be(task1)
    queue.dequeue() should be(task2)
    queue.dequeue() should be(task3)
    queue.dequeue() should be(task4)

    queue.enqueue(task3, Some( "hoge" ))
    queue.enqueue(task2, Some( "hoge" ))
    queue.enqueue(task1, Some( "hoge" ))
    queue.enqueue(task4, Some( "hoge" ))
    queue.dequeue should be(task3)
    queue.dequeue should be(task2)
    queue.dequeue should be(task1)
    queue.dequeue should be(task4)
  }
  it should "dequeue tasks group by group" in {
    val queue = new BalancingQueue()
    val task11 = Task("id11", "taskType1", "content1")
    val task12 = Task("id12", "taskType1", "content2")
    val task13 = Task("id13", "taskType1", "content3")
    val task14 = Task("id14", "taskType1", "content4")

    val task21 = Task("id21", "taskType1", "content1")
    val task22 = Task("id22", "taskType1", "content2")

    val task31 = Task("id31", "taskType1", "content1")
    val task32 = Task("id32", "taskType1", "content2")
    val task33 = Task("id33", "taskType1", "content3")

    val task41 = Task("id41", "taskType1", "content1")

    queue.enqueue(task11, Some( "banana" ))
    queue.enqueue(task12, Some( "banana" ))
    queue.enqueue(task13, Some( "banana" ))
    queue.enqueue(task14, Some( "banana" ))
    queue.enqueue(task21, Some( "orange" ))
    queue.enqueue(task22, Some( "orange" ))
    queue.enqueue(task31, Some( "apple" ))
    queue.enqueue(task32, Some( "apple" ))
    queue.enqueue(task33, Some( "apple" ))
    queue.enqueue(task41, Some( "cherry" ))

    queue.dequeue() should be(task11)
    queue.dequeue() should be(task21)
    queue.dequeue() should be(task31)
    queue.dequeue() should be(task41)
    queue.dequeue() should be(task12)
    queue.dequeue() should be(task22)
    queue.dequeue() should be(task32)
    queue.dequeue() should be(task13)
    queue.dequeue() should be(task33)
    queue.dequeue() should be(task14)

    queue.isEmpty should be(true)



    queue.enqueue(task41, Some( "cherry" ))
    queue.enqueue(task33, Some( "apple" ))
    queue.enqueue(task12, Some( "banana" ))
    queue.enqueue(task11, Some( "banana" ))
    queue.enqueue(task22, Some( "orange" ))
    queue.enqueue(task31, Some( "apple" ))
    queue.enqueue(task21, Some( "orange" ))
    queue.enqueue(task14, Some( "banana" ))
    queue.enqueue(task13, Some( "banana" ))
    queue.enqueue(task32, Some( "apple" ))

    queue.dequeue() should be(task41)
    queue.dequeue() should be(task33)
    queue.dequeue() should be(task12)
    queue.dequeue() should be(task22)
    queue.dequeue() should be(task31)
    queue.dequeue() should be(task11)
    queue.dequeue() should be(task21)
    queue.dequeue() should be(task32)
    queue.dequeue() should be(task14)
    queue.dequeue() should be(task13)

    queue.isEmpty should be(true)
  }

  it should "delete by id" in {
    val queue = new BalancingQueue()
    val task1 = Task("id1", "taskType1", "content1")
    val task2 = Task("id2", "taskType1", "content2")
    val task3 = Task("id3", "taskType1", "content3")
    val task4 = Task("id4", "taskType1", "content4")
    queue.enqueue(task1, Some( "hoge" ))
    queue.enqueue(task2, Some( "hoge" ))
    queue.enqueue(task3, Some( "hoge" ))
    queue.enqueue(task4, Some( "hoge" ))

    queue.deleteById( "id3" )
    queue.dequeue() should be(task1)
    queue.dequeue() should be(task2)
    queue.dequeue() should be(task4)

    queue.isEmpty should be( true )
  }
  it should "delete by group" in {
    val queue = new BalancingQueue()
    val task1 = Task("id1", "taskType1", "content1")
    val task2 = Task("id2", "taskType1", "content2")
    val task3 = Task("id3", "taskType1", "content3")
    val task4 = Task("id4", "taskType1", "content4")
    queue.enqueue(task1, Some( "hoge" ))
    queue.enqueue(task2, Some( "hoge" ))
    queue.enqueue(task3, Some( "moge" ))
    queue.enqueue(task4, Some( "hoge" ))

    queue.deleteByGroup( "hoge" )
    queue.isEmpty should be( false )
    queue.dequeue() should be(task3)
    queue.isEmpty should be( true )
  }

  it should "serialize" in {
    val queue = new BalancingQueue()
    val task1 = Task("id1", "taskType1", "content1")
    val task2 = Task("id2", "taskType1", "content2")
    val task3 = Task("id3", "taskType1", "content3")
    val task4 = Task("id4", "taskType1", "content4")

    queue.enqueue(task1, Some( "hoge" ))
    queue.enqueue(task2, Some( "hoge" ))
    queue.enqueue(task3, Some( "moge" ))
    queue.enqueue(task4, Some( "hoge" ))

    val serialization = SerializationExtension(system)
    val serializer = serialization.findSerializerFor(queue)
    val bytes      = serializer.toBinary(queue)
    val back       = serializer.fromBinary(bytes, manifest = None)
    back should be(queue)

    queue.dequeue()
    back should not be(queue)
    val bytes2      = serializer.toBinary(queue)
    val back2       = serializer.fromBinary(bytes, manifest = None)
    back2 should not be(queue)

  }
}
