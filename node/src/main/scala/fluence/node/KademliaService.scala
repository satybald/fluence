/*
 * Copyright (C) 2017  Fluence Labs Limited
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fluence.node

import java.time.Instant

import cats.data.StateT
import cats.{ MonadError, Parallel }
import fluence.kad.{ Bucket, Kademlia, Siblings }
import fluence.kad.protocol.{ KademliaRpc, Key, Node }
import monix.eval.{ MVar, Task }
import monix.execution.atomic.AtomicAny

import scala.collection.concurrent.TrieMap
import scala.language.implicitConversions

// TODO: write unit tests
/**
 * Kademlia service to be launched as a singleton on local node.
 *
 * @param nodeId Current node ID
 * @param contact Node's contact to advertise
 * @param client Getter for RPC calling of another nodes
 * @param conf Kademlia conf
 * @param checkNode Node could be saved to RoutingTable only if checker returns F[ true ]
 * @tparam C Contact info
 */
class KademliaService[C](
    nodeId: Key,
    contact: Task[C],
    client: C ⇒ KademliaRpc[Task, C],
    conf: KademliaConf,
    checkNode: Node[C] ⇒ Task[Boolean]
) extends Kademlia[Task, C](nodeId, conf.parallelism, conf.pingExpiresIn, checkNode)(
  implicitly[MonadError[Task, Throwable]],
  implicitly[Parallel[Task, Task]],
  KademliaService.bucketOps(conf.maxBucketSize),
  KademliaService.siblingsOps(nodeId, conf.maxSiblingsSize)
) {
  /**
   * Returns a network wrapper around a contact C, allowing querying it with Kademlia protocol.
   *
   * @param contact Description on how to connect to remote node
   * @return
   */
  override def rpc(contact: C): KademliaRpc[Task, C] = client(contact)

  /**
   * How to promote this node to others.
   *
   * @return
   */
  override def ownContact: Task[Node[C]] =
    contact.map(c ⇒ Node(nodeId, Instant.now(), c))

}

object KademliaService {
  /**
   * Performs atomic update on a MVar, blocking asynchronously if another update is in progress.
   *
   * @param mvar State variable
   * @param mod Modifier
   * @param updateRead Callback to update read model
   * @tparam S State
   * @tparam T Return value
   */
  private def runOnMVar[S, T](mvar: MVar[S], mod: StateT[Task, S, T], updateRead: S ⇒ Unit): Task[T] =
    mvar.take.flatMap { init ⇒
      // Run modification
      mod.run(init).onErrorHandleWith { err ⇒
        // In case modification failed, write initial value back to MVar
        mvar.put(init).flatMap(_ ⇒ Task.raiseError(err))
      }
    }.flatMap {
      case (updated, value) ⇒
        // Update read and write states
        updateRead(updated)
        mvar.put(updated).map(_ ⇒ value)
    }

  /**
   * Builds asynchronous bucket ops with $maxBucketSize nodes in each bucket.
   *
   * @param maxBucketSize Max number of nodes in each bucket
   * @tparam C Node contacts type
   */
  def bucketOps[C](maxBucketSize: Int): Bucket.WriteOps[Task, C] =
    new Bucket.WriteOps[Task, C] {
      private val writeState = TrieMap.empty[Int, MVar[Bucket[C]]]
      private val readState = TrieMap.empty[Int, Bucket[C]]

      override protected def run[T](bucketId: Int, mod: StateT[Task, Bucket[C], T]): Task[T] =
        runOnMVar(
          writeState.getOrElseUpdate(bucketId, MVar(read(bucketId))),
          mod,
          readState.update(bucketId, _: Bucket[C])
        )

      override def read(bucketId: Int): Bucket[C] =
        readState.getOrElseUpdate(bucketId, Bucket[C](maxBucketSize))
    }

  /**
   * Builds asynchronous sibling ops with $maxSiblings nodes max.
   *
   * @param nodeId Siblings are sorted by distance to this nodeId
   * @param maxSiblings Max number of closest siblings to store
   * @tparam C Node contacts type
   */
  def siblingsOps[C](nodeId: Key, maxSiblings: Int): Siblings.WriteOps[Task, C] =
    new Siblings.WriteOps[Task, C] {
      private val readState = AtomicAny(Siblings[C](nodeId, maxSiblings))
      private val writeState = MVar(Siblings[C](nodeId, maxSiblings))

      override protected def run[T](mod: StateT[Task, Siblings[C], T]): Task[T] =
        runOnMVar(
          writeState,
          mod,
          readState.set
        )

      override def read: Siblings[C] =
        readState.get
    }
}
