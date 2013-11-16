/*
 Copyright 2013 Twitter, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.twitter.summingbird.online

import org.scalacheck._
import Gen._
import Arbitrary._
import org.scalacheck.Prop._

import com.twitter.util.{Return, Throw, Future, Try}

object QueueChannelLaws extends Properties("Channel") {

  property("Putting into a BoundedQueue gets size right") = forAll { (items: List[String]) =>
    val q = Channel[String]()
    q.putAll(items)
    q.size == items.size
  }
  property("not spill if capacity is enough") = forAll { (items: List[Int]) =>
    val q = Channel[Int]()
    q.putAll(items)
    q.trimTo(items.size).size == 0
  }
  property("Work with indepent additions") = forAll { (items: List[Int]) =>
    val q = Channel[Int]()
    items.map(q.put(_)) == (1 to items.size).toList
  }
  property("spill all with zero capacity") = forAll { (items: List[Int]) =>
    val q = Channel[Int]()
    q.putAll(items)
    q.trimTo(0) == items
  }
  property("Channel works with finished futures") = forAll { (items: List[Int]) =>
    val q = Channel.linkedBlocking[(Int,Try[Int])]
    items.foreach { i => q.put((i, Try(i*i))) }
    q.foldLeft((0, true)) { case ((cnt, good), (i, ti)) =>
      ti match {
        case Return(ii) => (cnt + 1, good)
        case Throw(e) => (cnt + 1, false)
      }
    } == (items.size, true)
  }
  property("Channel.linkedNonBlocking works") = forAll { (items: List[Int]) =>
    val q = Channel.linkedNonBlocking[(Int,Try[Int])]
    items.foreach { i => q.put((i, Try(i*i))) }
    q.foldLeft((0, true)) { case ((cnt, good), (i, ti)) =>
      ti match {
        case Return(ii) => (cnt + 1, good)
        case Throw(e) => (cnt + 1, false)
      }
    } == (items.size, true)
  }
  property("Channel foreach works") = forAll { (items: List[Int]) =>
    // Make sure we can fit everything
    val q = Channel.arrayBlocking[(Int,Try[Int])](items.size + 1)
    items.foreach { i => q.put((i,Try(i*i))) }
    var works = true
    q.foreach { case (i, Return(ii)) =>
      works = works && (ii == i*i)
    }
    works && (q.size == 0)
  }
  property("Channel foldLeft works") = forAll { (items: List[Int]) =>
    // Make sure we can fit everything
    val q = Channel.arrayBlocking[(Int,Try[Int])](items.size + 1)
    items.foreach { i => q.put((i,Try(i*i))) }
    q.foldLeft(true) { case (works, (i, Return(ii))) =>
      (ii == i*i)
    } && (q.size == 0)
  }

  property("Channel poll + size is correct") = forAll { (items: List[Int]) =>
    // Make sure we can fit everything
    val q = Channel[Int]()
    items.map { i =>
      q.put(i)
      val size = q.size
      if(i % 2 == 0) {
        // do a poll test
        q.poll match {
          case None => q.size == 0
          case Some(_) => q.size == (size - 1)
        }
      }
      else true
    }.forall(identity)
  }
}
