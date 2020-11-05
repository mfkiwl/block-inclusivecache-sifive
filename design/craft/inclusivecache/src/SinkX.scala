/*
 * Copyright 2019 SiFive, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You should have received a copy of LICENSE.Apache2 along with
 * this software. If not, you may obtain a copy at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sifive.blocks.inclusivecache

import Chisel._
import freechips.rocketchip.tilelink._

class SinkXRequest(params: InclusiveCacheParameters) extends InclusiveCacheBundle(params)
{
  val address = UInt(width = params.inner.bundle.addressBits)
  def dump() = {
    DebugPrint(params, "SinkXRequest: address: %x\n", address)
  }
}

class SinkX(params: InclusiveCacheParameters) extends Module
{
  val io = new Bundle {
    val req = Decoupled(new FullRequest(params))
    val x = Decoupled(new SinkXRequest(params)).flip
  }

  when (io.req.fire()) {
    DebugPrint(params, "sinkX req ")
    io.req.bits.dump
  }

  when (io.x.fire()) {
    DebugPrint(params, "sinkX X ")
    io.x.bits.dump
  }
    
  val x = Queue(io.x, 1)
  val (tag, set, offset) = params.parseAddress(x.bits.address)

  x.ready := io.req.ready
  io.req.valid := x.valid
  params.ccover(x.valid && !x.ready, "SINKX_STALL", "Backpressure when accepting a control message")

  io.req.bits.prio   := Vec(UInt(1, width=3).asBools) // same prio as A
  io.req.bits.control:= Bool(true)
  io.req.bits.opcode := UInt(0)
  io.req.bits.param  := UInt(0)
  io.req.bits.size   := UInt(params.offsetBits)
  // The source does not matter, because a flush command never allocates a way.
  // However, it must be a legal source, otherwise assertions might spuriously fire.
  io.req.bits.source := UInt(params.inner.client.clients.map(_.sourceId.start).min)
  io.req.bits.offset := UInt(0)
  io.req.bits.set    := set
  io.req.bits.tag    := tag
}