package actorbinarytree

import BinaryTreeSet._
import akka.actor.{Actor, ActorRef, Props}

object BinaryTreeNode {

  trait Position

  case object Left extends Position

  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)

  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode], elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {

  import actorbinarytree.BinaryTreeNode._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved


  def receive = normal

  val normal: Receive = {
    case Contains(requester, id, elemToFind) => {

      if (elem != elemToFind || (elem==elemToFind && removed)) {

        val child = childToVisit(elemToFind)

        if (subtrees.contains(child)) {
          subtrees(child) ! Contains(requester, id, elemToFind)
        }
        else {
          requester ! ContainsResult(id, false)
        }

      }
      else {
        requester ! ContainsResult(id, true)
      }
    }

    case Insert(requester, id, elemToInsert) => {

      if (elem != elemToInsert || (elem==elemToInsert && removed)) {

        val child = childToVisit(elemToInsert)

        if (subtrees.contains(child)) {
          subtrees(child) ! Insert(requester, id, elemToInsert)
        }
        else {
          subtrees += (child -> context.actorOf(BinaryTreeNode.props(elemToInsert, false)))
          requester ! OperationFinished(id)
        }

      }
      else {
        requester ! OperationFinished(id)
      }

    }

    case Remove(requester, id, elemToRemove) => {

      if (elem != elemToRemove || (elem==elemToRemove && removed)) {

        val child = childToVisit(elemToRemove)

        if (subtrees.contains(child)) {
          subtrees(child) ! Remove(requester, id, elemToRemove)
        }
        else {
          requester ! OperationFinished(id)
        }
      }
      else {
        removed = true
        requester ! OperationFinished(id)
      }
    }

    case CopyTo(newRoot) => {
      if (!removed){
        newRoot ! Insert(self, 0, elem)
      }
      if (removed && subtrees.isEmpty){
        sender ! CopyFinished
      }
      else{
        context.become(copying(subtrees.values.toSet, insertConfirmed = removed, sender))
      }
      subtrees.values foreach (_ ! CopyTo(newRoot))
    }
  }

  def childToVisit(elemToFind: Int): Position = {
    if (elemToFind > elem) Right
    else Left
  }

  def copying(expected: Set[ActorRef], insertConfirmed: Boolean, originator: ActorRef): Receive = {
    case OperationFinished(_) =>
      if (expected.isEmpty) {
        originator ! CopyFinished
        context.become(normal)
      } else {
        context.become(copying(expected, insertConfirmed = true, originator))
      }
    case CopyFinished =>
      val newExpected = expected - sender
      if (newExpected.isEmpty && insertConfirmed) {
        originator ! CopyFinished
        context.become(normal)
      } else {
        context.become(copying(newExpected, insertConfirmed, originator))
      }
  }
}
