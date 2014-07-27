package jp.co.shanon.malba.client

import scala.concurrent._
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.AkkaException
import akka.actor.ActorRef
import akka.actor.ReceiveTimeout
import akka.routing.FromConfig
import akka.pattern._
import akka.util.Timeout
import jp.co.shanon.malba.worker.MalbaProtocol.IntenalServerError

case object NoResponse

class MalbaRequestHandler(router: ActorRef, timeout: FiniteDuration, maxRetry: Int) extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {
    case request => 
      context.setReceiveTimeout(timeout)
      context.become(waitForResponse(sender(), request, 1))
      router ! request
  }

  def waitForResponse(from: ActorRef, request: Any, tryCount: Int): Receive = {
    case ReceiveTimeout if maxRetry > tryCount =>
      log.warning(s"No response from Malba, retrying ${tryCount.toString} times.")
      context.become(waitForResponse(from, request, tryCount + 1))
      router ! request
      context.stop( self )

    case ReceiveTimeout =>
      log.error(s"Timeout after tyring ${tryCount.toString} times.")
      from ! NoResponse
      context.stop( self )

    case IntenalServerError(message) =>
      log.error(s"Internal server error after tyring ${tryCount.toString} times. Message: ${message}")
      context.become(waitForResponse(from, request, tryCount + 1))
      router ! request

    case response =>
      from ! response
      context.stop( self )
  }
}
