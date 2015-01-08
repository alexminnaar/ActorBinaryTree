package actorbinarytree

import actorbinarytree.BinaryTreeNode.{CopyTo, CopyFinished}
import akka.actor._

import scala.collection.immutable.Queue


object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef

    def id: Int

    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  case object GC

  case class ContainsResult(id: Int, result: Boolean) extends OperationReply

  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {

  import actorbinarytree.BinaryTreeSet._
  import actorbinarytree.BinaryTreeSet._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  var pendingOperations: Queue[Operation] = Queue.empty[Operation]

  var pendingQueue = Queue.empty[Operation]

  def receive = normal

  val normal: Receive = {
    case operation: Operation => root ! operation

    case GC => {
      val newRoot = createRoot
      root ! CopyTo(newRoot)
      context.become(garbageCollecting(newRoot))
    }
  }


  def garbageCollecting(newRoot: ActorRef): Receive = {

    case operation: Operation => pendingQueue.enqueue(operation)

    case CopyFinished => {
      root ! PoisonPill
      val newRoot = createRoot
      root = newRoot

      pendingQueue.map(root ! _)
      pendingQueue = Queue.empty

      context.become(normal)
    }
  }

}

