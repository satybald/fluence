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

package fluence.btree.server.commands

import cats.MonadError
import fluence.btree.client.Key
import fluence.btree.client.protocol.BTreeRpc.SearchCallback
import fluence.btree.server.core.{ BranchNode, TreeCommand }

/**
 * Abstract command implementation for searching some value in BTree (by client search key).
 * Search key is stored at the client. BTree server will never know search key.
 *
 * @param searchCallback A search callback that allowed the server asks next child node index
 * @param ME              Monad error
 * @tparam F              The type of effect, box for returning value
 */
abstract class BaseSearchCommand[F[_]](searchCallback: SearchCallback[F])(implicit ME: MonadError[F, Throwable])
  extends TreeCommand[F, Key] {

  override def nextChildIndex(branch: BranchNode[Key, _]): F[Int] =
    searchCallback.nextChildIndex(branch.keys, branch.childsChecksums)
}
