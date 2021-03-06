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

package fluence.btree.client.core

/**
 * An interface to search tree.
 *
 * @tparam F An effect, with MonadError
 * @tparam K The type of key
 * @tparam V The type of value
 */
trait SearchTree[F[_], K, V] {

  def get(key: K): F[Option[V]]

  def put(key: K, value: V): F[Option[V]]

  def delete(key: K): F[Option[V]]

}
