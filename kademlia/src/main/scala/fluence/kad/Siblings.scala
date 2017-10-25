package fluence.kad

import cats.Show

import scala.collection.SortedSet
import cats.syntax.eq._

case class Siblings[C] private (nodes: SortedSet[Node[C]], maxSize: Int) {

  lazy val isFull: Boolean = nodes.size >= maxSize

  lazy val size: Int = nodes.size

  def isEmpty: Boolean = nodes.isEmpty

  def nonEmpty: Boolean = nodes.nonEmpty

  def find(key: Key): Option[Node[C]] = nodes.find(_.key === key)

  def contains(key: Key): Boolean = nodes.exists(_.key === key)

  def add(node: Node[C]): Siblings[C] =
    copy((nodes + node).take(maxSize))
}

object Siblings {
  implicit def show[C](implicit ks: Show[Key]): Show[Siblings[C]] =
    s ⇒ s.nodes.toSeq.map(_.key).map(ks.show).mkString(s"\nSiblings: ${s.size}\n\t", "\n\t", "")

  def apply[C](nodeId: Key, maxSize: Int): Siblings[C] = {
    implicit val ordering: Ordering[Node[C]] = Node.relativeOrdering(nodeId)
    new Siblings[C](SortedSet.empty, maxSize)
  }
}