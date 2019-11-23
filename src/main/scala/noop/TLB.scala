package noop

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import bus.simplebus._
import bus.axi4._
import utils._

trait Sv39Const extends HasNOOPParameter{
  val Level = 3
  val offLen  = 12
  val ppn0Len = 9
  val ppn1Len = 9
  val ppn2Len = PAddrBits - offLen - ppn0Len - ppn1Len // 2
  val ppnLen = ppn2Len + ppn1Len + ppn0Len
  val vpn2Len = 9
  val vpn1Len = 9
  val vpn0Len = 9
  val vpnLen = vpn2Len + vpn1Len + vpn0Len
  
  //val paddrLen = PAddrBits
  //val vaddrLen = VAddrBits
  val satpLen = XLEN
  val satpModeLen = 4
  val asidLen = 16
  val flagLen = 8

  val ptEntryLen = XLEN
  val satpResLen = XLEN - ppnLen - satpModeLen - asidLen
  //val vaResLen = 25 // unused
  //val paResLen = 25 // unused 
  val pteResLen = XLEN - ppnLen - 2 - flagLen

  def vaBundle = new Bundle {
    val vpn2 = UInt(vpn2Len.W)
    val vpn1 = UInt(vpn1Len.W)
    val vpn0 = UInt(vpn0Len.W)
    val off  = UInt( offLen.W)
  }

  def vaBundle2 = new Bundle {
    val vpn  = UInt(vpnLen.W)
    val off  = UInt(offLen.W)
  }

  def vaBundle3 = new Bundle {
    val vpn = UInt(vpnLen.W)
    val off = UInt(offLen.W)
  }

  def vpnBundle = new Bundle {
    val vpn2 = UInt(vpn2Len.W)
    val vpn1 = UInt(vpn1Len.W)
    val vpn0 = UInt(vpn0Len.W)
  }

  def paBundle = new Bundle {
    val ppn2 = UInt(ppn2Len.W)
    val ppn1 = UInt(ppn1Len.W)
    val ppn0 = UInt(ppn0Len.W)
    val off  = UInt( offLen.W)
  }

  def paBundle2 = new Bundle {
    val ppn  = UInt(ppnLen.W)
    val off  = UInt(offLen.W)
  }
  
  def paBundle3 = new Bundle {
    val ppn2 = UInt(ppn2Len.W)
    val ppn1 = UInt(ppn1Len.W)
    val ppn0 = UInt(ppn0Len.W)
    val off  = UInt( offLen.W)
  }

  def paddrApply(ppn: UInt, vpnn: UInt):UInt = {
    Cat(Cat(ppn, vpnn), 0.U(3.W))
  }
  
  def pteBundle = new Bundle {
    val reserved  = UInt(pteResLen.W)
    val ppn  = UInt(ppnLen.W)
    val rsw  = UInt(2.W)
    val flag = new Bundle {
      val d    = UInt(1.W)
      val a    = UInt(1.W)
      val g    = UInt(1.W)
      val u    = UInt(1.W)
      val x    = UInt(1.W)
      val w    = UInt(1.W)
      val r    = UInt(1.W)
      val v    = UInt(1.W)
    }
  }

  def satpBundle = new Bundle {
    val mode = UInt(satpModeLen.W)
    val asid = UInt(asidLen.W)
    val res = UInt(satpResLen.W)
    val ppn  = UInt(ppnLen.W)
  }

  def flagBundle = new Bundle {
    val d    = Bool()//UInt(1.W)
    val a    = Bool()//UInt(1.W)
    val g    = Bool()//UInt(1.W)
    val u    = Bool()//UInt(1.W)
    val x    = Bool()//UInt(1.W)
    val w    = Bool()//UInt(1.W)
    val r    = Bool()//UInt(1.W)
    val v    = Bool()//UInt(1.W)
  }
/*
  def vmMux(userBits: Int = 0, en: Bool, enYes: SimpleBusReqBundle, enNo: SimpleBusReqBundle) = {
    val res = Wire(new SimpleBusReqBundle(userBits))
    res.addr := Mux(en, enYes.addr, enNo.addr)
    res.size := Mux(en, enYes.size, enNo.size)
    res.cmd  := Mux(en,  enYes.cmd,  enNo.cmd)
    res.wmask:= Mux(en,enYes.wmask,enNo.wmask)
    res.wdata:= Mux(en,enYes.wdata,enNo.wdata)
    if(userBits > 0) { res.user.map(_ := Mux(en, enYes.user.getOrElse(0.U),enNo.user.getOrElse(0.U))) }
    res
  }
*/
  def maskPaddr(ppn:UInt, vaddr:UInt, mask:UInt) = {
    MaskData(vaddr, Cat(ppn, 0.U(offLen.W)), Cat(Fill(ppn2Len, 1.U(1.W)), mask, 0.U(offLen.W)))
  }

  def MaskEQ(mask: UInt, pattern: UInt, vpn: UInt) = {
    (Cat("h1ff".U(vpn2Len.W), mask) & pattern) === (Cat("h3".U(vpn2Len.W), mask) & vpn)
  }

}

case class TLBConfig (
  name: String = "tlb",
  userBits: Int = 0,

  totalSize: Int = 128, 
  entryNum: Int = 4
)

sealed trait HasTlbConst extends Sv39Const{
  implicit val tlbConfig: TLBConfig

  val AddrBits: Int
  val PAddrBits: Int
  val VAddrBits: Int
  val XLEN: Int

  val tlbname = tlbConfig.name
  val userBits = tlbConfig.userBits

  val NTLB = tlbConfig.entryNum
  val NTLBBits = log2Up(NTLB)

  val maskLen = vpn0Len + vpn1Len  // 18
  val metaLen = vpnLen + asidLen + maskLen + flagLen // 27 + 16 + 18 + 8 = 69, is asid necessary 
  val dataLen = ppnLen + PAddrBits // 

  val debug = true && tlbname == "dtlb"

  def metaBundle = new Bundle {
    val vpn = UInt(vpnLen.W)
    val asid = UInt(asidLen.W)
    val mask = UInt(maskLen.W) // to support super page
    val flag = UInt(flagLen.W)
  }

  def dataBundle = new Bundle {
    val ppn = UInt(ppnLen.W)
    val addr = UInt(PAddrBits.W) // pte addr, used to write back pte when flag changes (flag.d, flag.v)
  }

}

sealed abstract class TlbBundle(implicit tlbConfig: TLBConfig) extends Bundle with HasNOOPParameter with HasTlbConst with Sv39Const
sealed abstract class TlbModule(implicit tlbConfig: TLBConfig) extends Module with HasNOOPParameter with HasTlbConst with Sv39Const with HasCSRConst

class TLBMeta(implicit val tlbConfig: TLBConfig) extends TlbModule { //TODO: another try that treats TLBMeta as Bunlde not Module
  val io = IO(new Bundle {
    val metas = Output(Vec(NTLB, UInt(metaLen.W)))

    val write = new Bundle {
      val wen = Input(Bool())
      val dest = Input(UInt(NTLBBits.W))
      val vpn = Input(UInt(vpnLen.W))
      val asid = Input(UInt(asidLen.W))
      val mask = Input(UInt(maskLen.W))
      val flag = Input(UInt(flagLen.W))
    }

    val ready = Output(Bool())
  })

  val metas = Reg(Vec(NTLB, UInt(metaLen.W)))
  io.metas := metas

  //val reset = WireInit(false.B)
  val resetState = RegInit(true.B)//RegEnable(true.B, init = true.B, reset)
  val (resetSet, resetFinish) = Counter(resetState, NTLB)
  when (resetFinish) { resetState := false.B }

  val writeWen = io.write.wen//WireInit(false.B)
  val writeDest = io.write.dest//WireInit(0.U(NTLBBits.W))
  val writeData = Cat(io.write.vpn, io.write.asid, io.write.mask, io.write.flag)//WireInit(0.U(metaLen.W))

  //def read(addr: UInt) : UInt = metas(addr)
  //def write(addr: UInt, en: Bool, vpn: UInt, asid: UInt, mask: UInt, flag: UInt) = {
  //  writeDest := addr; writeWen := en; writeData := Cat(vpn, asid, mask, flag) 
  //}

  val wen = Mux(resetState, true.B, writeWen)
  val dest = Mux(resetState, resetSet, writeDest)
  val data = Mux(resetState, 0.U, writeData)

  when (wen) { metas(dest) := data }

  io.ready := !resetState
  def rready() = !resetState
  def wready() = !resetState
}

class TLBData(implicit val tlbConfig: TLBConfig) extends TlbModule {

  val io = IO(new Bundle {
    val datas = Output(Vec(NTLB, UInt(dataLen.W)))

    val write = new Bundle {
      val wen = Input(Bool())
      val dest = Input(UInt(NTLBBits.W))
      val ppn = Input(UInt(ppnLen.W))
      val pteaddr = Input(UInt(PAddrBits.W))
    }
  })
  
  val datas = Reg(Vec(NTLB, UInt(dataLen.W)))
  io.datas := datas

  when (io.write.wen) { 
    datas(io.write.dest) := Cat(io.write.ppn, io.write.pteaddr) 
  }
  //def read(addr: UInt) : UInt = datas(addr)
  //def write(addr: UInt, en: Bool, ppn: UInt, pteaddr: UInt) = {
  //  when(en) { datas(addr) := Cat(ppn, pteaddr) }
  //}
}

class TLB(implicit val tlbConfig: TLBConfig) extends TlbModule{
  val io = IO(new Bundle {
    val in = Flipped(new SimpleBusUC(userBits = userBits, addrBits = VAddrBits))
    val out = new SimpleBusUC(userBits = userBits)

    val mem = new SimpleBusUC()
    val flush = Input(Bool()) 
    val csrMMU = new MMUIO
    val cacheEmpty = Input(Bool())
    val ipf = Output(Bool())
  })

  val satp = WireInit(0.U(XLEN.W))
  BoringUtils.addSink(satp, "CSRSATP")

  // tlb exec
  val tlbExec = Module(new TLBExec)

  tlbExec.io.flush := io.flush
  tlbExec.io.satp := satp
  tlbExec.io.mem <> io.mem
  tlbExec.io.pf <> io.csrMMU
  
  io.ipf := false.B

  // VM enable && io
  val vmEnable = satp.asTypeOf(satpBundle).mode === 8.U && (io.csrMMU.priviledgeMode < ModeM)

  def PipelineConnectTLB[T <: Data](left: DecoupledIO[T], right: DecoupledIO[T], rightOutFire: Bool, isFlush: Bool, vmEnable: Bool) = {
    val valid = RegInit(false.B)
    when (rightOutFire) { valid := false.B }
    when (left.valid && right.ready && vmEnable) { valid := true.B }
    when (isFlush) { valid := false.B }

    left.ready := right.ready
    right.bits <> RegEnable(left.bits, left.valid && right.ready)
    right.valid := valid //&& !isFlush
  }

  PipelineConnectTLB(io.in.req, tlbExec.io.in, tlbExec.io.isFinish, io.flush, vmEnable)
  when(!vmEnable) {
    tlbExec.io.out.ready := true.B // let existed request go out
    io.out.req.valid := io.in.req.valid
    io.in.req.ready := io.out.req.ready
    io.out.req.bits.addr := io.in.req.bits.addr(PAddrBits-1, 0)
    io.out.req.bits.size := io.in.req.bits.size
    io.out.req.bits.cmd := io.in.req.bits.cmd
    io.out.req.bits.wmask := io.in.req.bits.wmask
    io.out.req.bits.wdata := io.in.req.bits.wdata
    io.out.req.bits.user.map(_ := io.in.req.bits.user.getOrElse(0.U))
  }.otherwise {
    io.out.req <> tlbExec.io.out
  }
  io.out.resp <> io.in.resp

  // lsu need dtlb signals
  if(tlbname == "dtlb") {
    val alreadyOutFinish = RegEnable(true.B, init=false.B, tlbExec.io.out.valid && !tlbExec.io.out.ready)
    when(alreadyOutFinish && tlbExec.io.out.fire()) { alreadyOutFinish := false.B}
    val tlbFinish = (tlbExec.io.out.valid && !alreadyOutFinish) || tlbExec.io.pf.isPF()
    BoringUtils.addSource(tlbFinish, "DTLBFINISH")
    BoringUtils.addSource(io.csrMMU.isPF(), "DTLBPF")
    BoringUtils.addSource(vmEnable, "DTLBENABLE")
  }

  // instruction page fault
  if (tlbname == "itlb") {
    when (tlbExec.io.ipf && vmEnable) {
      tlbExec.io.out.ready := io.cacheEmpty && io.in.resp.ready
      io.out.req.valid := false.B
    }

    when (tlbExec.io.ipf && vmEnable && io.cacheEmpty) {
      io.in.resp.valid := true.B
      io.in.resp.bits.rdata := 0.U
      io.in.resp.bits.cmd := SimpleBusCmd.readLast
      io.in.resp.bits.user.map(_ := tlbExec.io.in.bits.user.getOrElse(0.U))
      io.ipf := tlbExec.io.ipf
    }
  }

  Debug() {
    if (debug) {
      printf("[TLB-"  + tlbname+ "]: Timer:%d---------\n", GTimer())
      printf("[TLB-"  + tlbname+ "]: InReq(%d, %d) InResp(%d, %d) OutReq(%d, %d) OutResp(%d, %d) vmEnable:%d mode:%d\n", io.in.req.valid, io.in.req.ready, io.in.resp.valid, io.in.resp.ready, io.out.req.valid, io.out.req.ready, io.out.resp.valid, io.out.resp.ready, vmEnable, io.csrMMU.priviledgeMode)
      printf("[TLB-"  + tlbname+ "]: InReq: addr:%x cmd:%d wdata:%x OutReq: addr:%x cmd:%x wdata:%x\n", io.in.req.bits.addr, io.in.req.bits.cmd, io.in.req.bits.wdata, io.out.req.bits.addr, io.out.req.bits.cmd, io.out.req.bits.wdata)
      printf("[TLB-"  + tlbname+ "]: OutResp: rdata:%x cmd:%x Inresp: rdata:%x cmd:%x\n", io.out.resp.bits.rdata, io.out.resp.bits.cmd, io.in.resp.bits.rdata, io.in.resp.bits.cmd)
      printf("[TLB-"  + tlbname+ "]: satp:%x flush:%d cacheEmpty:%d instrPF:%d loadPF:%d storePF:%d \n", satp, io.flush, io.cacheEmpty, io.ipf, io.csrMMU.loadPF, io.csrMMU.storePF)
    }
  }  

}

class TLBExec(implicit val tlbConfig: TLBConfig) extends TlbModule{
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new SimpleBusReqBundle(userBits = userBits, addrBits = VAddrBits)))
    val out = Decoupled(new SimpleBusReqBundle(userBits = userBits))

    val mem = new SimpleBusUC()
    val flush = Input(Bool()) 
    val satp = Input(UInt(XLEN.W))
    val pf = new MMUIO
    val ipf = Output(Bool())
    val isFinish = Output(Bool())
  })

  // meta & data
  val metasTLB = Module(new TLBMeta)
  val datasTLB = Module(new TLBData)
  val metas = RegEnable(metasTLB.io.metas, io.in.ready)
  val datas = RegEnable(datasTLB.io.datas, io.in.ready)

  // meta reset
  val flushTLB = WireInit(false.B)
  BoringUtils.addSink(flushTLB, "MOUFlushTLB")
  metasTLB.reset := reset.asBool || flushTLB
  Debug() {
    when(flushTLB && GTimer() > 77437080.U) {
      printf("%d sfence_vma req.pc:%x valid:%d\n", GTimer(), io.in.bits.addr, io.in.valid)
    }
  }

  // lazy renaming
  val req = io.in.bits
  val vpn = req.addr.asTypeOf(vaBundle2).vpn.asTypeOf(vpnBundle)
  val pf = io.pf
  val satp = io.satp.asTypeOf(satpBundle)

  // pf init
  pf.loadPF := false.B
  pf.storePF := false.B
  pf.addr := req.addr

  // check hit or miss
  val hitVec = VecInit(metas.map(m => m.asTypeOf(metaBundle).flag.asTypeOf(flagBundle).v && MaskEQ(m.asTypeOf(metaBundle).mask, m.asTypeOf(metaBundle).vpn, vpn.asUInt))).asUInt
  val hit = io.in.valid && hitVec.orR
  val miss = io.in.valid && !hitVec.orR

  val victimWaymask = if (NTLB > 1) (1.U << LFSR64()(log2Up(NTLB)-1,0)) else "b1".U
  val waymask = Mux(hit, hitVec, victimWaymask)

  // hit
  val hitMeta = Mux1H(waymask, metas).asTypeOf(metaBundle)
  val hitData = Mux1H(waymask, datas).asTypeOf(dataBundle)
  val hitFlag = hitMeta.flag.asTypeOf(flagBundle)
  val hitMask = hitMeta.mask
  // hit write back pte.flag
  val hitinstrPF = WireInit(false.B)
  val hitWB = hit && (!hitFlag.a || !hitFlag.d && req.isWrite()) && !hitinstrPF && !io.pf.isPF()
  val hitRefillFlag = Cat(req.isWrite().asUInt, 1.U(1.W), 0.U(6.W)) | hitFlag.asUInt
  val hitWBStore = RegEnable(Cat(0.U(10.W), hitData.ppn, 0.U(2.W), hitRefillFlag), hitWB)

  // hit permission check
  val hitCheck = hit /*&& hitFlag.v */&& !(pf.priviledgeMode === ModeU && !hitFlag.u) && !(pf.priviledgeMode === ModeS && hitFlag.u && !pf.status_sum)
  val hitExec = hitCheck && hitFlag.x
  val hitLoad = hitCheck && (hitFlag.r || pf.status_mxr && hitFlag.x)
  val hitStore = hitCheck && hitFlag.w
  
  val isAMO = WireInit(false.B)
  if (tlbname == "dtlb") {
    BoringUtils.addSink(isAMO, "ISAMO")
  }

  io.pf.loadPF := false.B
  io.pf.storePF := false.B
  io.pf.addr := req.addr

  if (tlbname == "itlb") { hitinstrPF := !hitExec  && hit}
  if (tlbname == "dtlb") { 
    pf.loadPF := !hitLoad && req.isRead() && hit && !isAMO
    pf.storePF := (!hitStore && req.isWrite() && hit) || (!hitLoad && req.isRead() && hit && isAMO)
  }

  // miss
  val s_idle :: s_memReadReq :: s_memReadResp :: s_write_pte :: s_wait_resp :: Nil = Enum(5)
  val state = RegInit(s_idle)
  val level = RegInit(Level.U(log2Up(Level).W))
  
  val memRespStore = Reg(UInt(XLEN.W))
  val missMask = WireInit("h3ffff".U(maskLen.W))
  val missMaskStore = Reg(UInt(maskLen.W))
  val missMetaRefill = WireInit(false.B)
  val missRefillFlag = WireInit(0.U(8.W))
  val memRdata = io.mem.resp.bits.rdata.asTypeOf(pteBundle)
  val raddr = Reg(UInt(PAddrBits.W))
  val alreadyOutFire = RegEnable(true.B, init = false.B, io.out.fire)

  //handle flush
  val needFlush = RegInit(false.B)
  val ioFlush = io.flush || flushTLB
  val isFlush = needFlush || ioFlush
  when (ioFlush && (state =/= s_idle)) { needFlush := true.B}
  when (io.out.fire() && needFlush) { needFlush := false.B}

  val missIPF = RegInit(false.B)

  // state machine to handle miss(ptw) and pte-writing-back
  switch (state) {
    is (s_idle) {
      when (!ioFlush && hitWB) {
        state := s_write_pte
        needFlush := false.B
        alreadyOutFire := false.B
      }.elsewhen (miss && !ioFlush) {
        state := s_memReadReq
        raddr := paddrApply(satp.ppn, vpn.vpn2) //
        level := Level.U
        needFlush := false.B
        alreadyOutFire := false.B
      }
    }

    is (s_memReadReq) { 
      when (isFlush) {
        state := s_idle
        needFlush := false.B
      }.elsewhen (io.mem.req.fire()) { state := s_memReadResp}
    }

    is (s_memReadResp) { 
      val missflag = memRdata.flag.asTypeOf(flagBundle)
      when (io.mem.resp.fire()) {
        when (isFlush) {
          state := s_idle
          needFlush := false.B
        }.elsewhen (!(missflag.r || missflag.x) && (level===3.U || level===2.U)) {
          when(!missflag.v || (!missflag.r && missflag.w)) { //TODO: fix needflush
            if(tlbname == "itlb") { state := s_wait_resp } else { state := s_idle }
            if(tlbname == "itlb") { missIPF := true.B }
            if(tlbname == "dtlb") { 
              pf.loadPF := req.isRead() && !isAMO 
              pf.storePF := req.isWrite() || isAMO 
            }  
            Debug() {
              if(debug) {
                printf("%d " + tlbname +" tlbException!!! ", GTimer())
                printf(p" req:${req}  Memreq:${io.mem.req}  MemResp:${io.mem.resp}")
                printf(" level:%d",level)
                printf("\n")
              }
            }
          }.otherwise {
            state := s_memReadReq
            raddr := paddrApply(memRdata.ppn, Mux(level === 3.U, vpn.vpn1, vpn.vpn0))
          }
        }.elsewhen (level =/= 0.U) { //TODO: fix needFlush
          val permCheck = missflag.v && !(pf.priviledgeMode === ModeU && !missflag.u) && !(pf.priviledgeMode === ModeS && missflag.u && !pf.status_sum)
          val permExec = permCheck && missflag.x
          val permLoad = permCheck && (missflag.r || pf.status_mxr && missflag.x)
          val permStore = permCheck && missflag.w
          val updateAD = !missflag.a || (!missflag.d && req.isWrite())
          val updateData = Cat( 0.U(56.W), req.isWrite(), 1.U(1.W), 0.U(6.W) )
          missRefillFlag := Cat(req.isWrite(), 1.U(1.W), 0.U(6.W)) | missflag.asUInt
          memRespStore := io.mem.resp.bits.rdata | updateData 
          if(tlbname == "itlb") {
            when (!permExec) { missIPF := true.B ; state := s_wait_resp}
            .otherwise { 
              state := Mux(updateAD, s_write_pte, s_wait_resp)
              missMetaRefill := true.B
            }
          }
          if(tlbname == "dtlb") {
            when((!permLoad && req.isRead()) || (!permStore && req.isWrite())) { 
              state := s_idle
              pf.loadPF := req.isRead() && !isAMO
              pf.storePF := req.isWrite() || isAMO
            }.otherwise {
              state := Mux(updateAD, s_write_pte, s_wait_resp)
              missMetaRefill := true.B
            }
          }
          missMask := Mux(level===3.U, 0.U(maskLen.W), Mux(level===2.U, "h3fe00".U(maskLen.W), "h3ffff".U(maskLen.W)))
          missMaskStore := missMask
        }
        level := level - 1.U
      }
    }

    is (s_write_pte) {
      when (isFlush) {
        state := s_idle
        needFlush := false.B
      }.elsewhen (io.mem.req.fire()) { state := s_wait_resp }
    }

    is (s_wait_resp) { when (io.out.fire() || ioFlush || alreadyOutFire){
      state := s_idle
      missIPF := false.B
      alreadyOutFire := false.B
    }}
  }

  // mem
  val cmd = Mux(state === s_write_pte, SimpleBusCmd.write, SimpleBusCmd.read)
  io.mem.req.bits.apply(addr = Mux(hitWB, hitData.addr, raddr), cmd = cmd, size = (if (XLEN == 64) "b11".U else "b10".U), wdata =  Mux( hitWB, hitWBStore, memRespStore), wmask = 0xff.U)
  io.mem.req.valid := ((state === s_memReadReq || state === s_write_pte) && !isFlush)
  io.mem.resp.ready := true.B

  // meta & data refill . TODO: try to wrap the below by a method
  val wdest = OHToUInt(waymask)
  //metasTLB.write(addr = wdest, en = (missMetaRefill && !isFlush) || (hitWB && state === s_idle && !isFlush), vpn = vpn.asUInt, asid = Mux(hitWB, hitMeta.asid, satp.asid), mask = Mux(hitWB, hitMask, missMask), flag = Mux(hitWB, hitRefillFlag, missRefillFlag))
  metasTLB.io.write.wen := (missMetaRefill && !isFlush) || (hitWB && state === s_idle && !isFlush)
  metasTLB.io.write.dest := wdest
  metasTLB.io.write.vpn := vpn.asUInt
  metasTLB.io.write.asid := Mux(hitWB, hitMeta.asid, satp.asid)
  metasTLB.io.write.mask := Mux(hitWB, hitMask, missMask)
  metasTLB.io.write.flag := Mux(hitWB, hitRefillFlag, missRefillFlag)
  //datasTLB.write(addr = wdest, en = missMetaRefill && !isFlush, ppn = memRdata.ppn, pteaddr = raddr)
  datasTLB.io.write.wen :=  missMetaRefill && !isFlush
  datasTLB.io.write.dest := wdest
  datasTLB.io.write.ppn := memRdata.ppn
  datasTLB.io.write.pteaddr := raddr

  // io
  io.out.bits := req
  io.out.bits.addr := Mux(hit, maskPaddr(hitData.ppn, req.addr(PAddrBits-1, 0), hitMask), maskPaddr(memRespStore.asTypeOf(pteBundle).ppn, req.addr(PAddrBits-1, 0), missMaskStore))
  io.out.valid := io.in.valid && Mux(hit && !hitWB, !io.pf.isPF() , state === s_wait_resp)// && !alreadyOutFire
  
  io.in.ready := io.out.ready && (state === s_idle) && !miss && !hitWB && metasTLB.io.ready//maybe be optimized

  io.ipf := Mux(hit, hitinstrPF, missIPF)
  io.isFinish := io.out.fire() || io.pf.isPF()

  Debug() {
    if (debug) {
      printf("[TLBExec-"  + tlbname+ "]: Timer:%d---------\n", GTimer())
      printf("[TLBExec-"  + tlbname+ "]: In(%d, %d) Out(%d, %d) InAddr:%x OutAddr:%x cmd:%d \n", io.in.valid, io.in.ready, io.out.valid, io.out.ready, req.addr, io.out.bits.addr, req.cmd)
      printf("[TLBExec-"  + tlbname+ "]: isAMO:%d io.Flush:%d FlushTLB:%d needFlush:%d alreadyOutFire:%d isFinish:%d\n",isAMO, io.flush, flushTLB, needFlush, alreadyOutFire, io.isFinish)
      printf("[TLBExec-"  + tlbname+ "]: hit:%d hitWB:%d hitVPN:%x hitFlag:%x hitPPN:%x hitRefillFlag:%x hitWBStore:%x hitCheck:%d hitExec:%d hitLoad:%d hitStore:%d\n", hit, hitWB, hitMeta.vpn, hitFlag.asUInt, hitData.ppn, hitRefillFlag, hitWBStore, hitCheck, hitExec, hitLoad, hitStore)
      printf("[TLBExec-"  + tlbname+ "]: miss:%d state:%d level:%d raddr:%x memRdata:%x missMask:%x missRefillFlag:%x missMetaRefill:%d\n", miss, state, level, raddr, memRdata.asUInt, missMask, missRefillFlag, missMetaRefill)
      printf("[TLBExec-"  + tlbname+ "]: meta/data: (0)%x|%b|%x (1)%x|%b|%x (2)%x|%b|%x (3)%x|%b|%x rread:%d\n", metas(0).asTypeOf(metaBundle).vpn, metas(0).asTypeOf(metaBundle).flag, datas(0).asTypeOf(dataBundle).ppn, metas(1).asTypeOf(metaBundle).vpn, metas(1).asTypeOf(metaBundle).flag, datas(1).asTypeOf(dataBundle).ppn, metas(2).asTypeOf(metaBundle).vpn, metas(2).asTypeOf(metaBundle).flag, datas(2).asTypeOf(dataBundle).ppn, metas(3).asTypeOf(metaBundle).vpn, metas(3).asTypeOf(metaBundle).flag, datas(3).asTypeOf(dataBundle).ppn, metasTLB.io.ready)
      printf("[TLBExec-"  + tlbname+ "]: meta: wen:%d dest:%x vpn:%x asid:%x mask:%x flag:%x\n", metasTLB.io.write.wen, metasTLB.io.write.dest, metasTLB.io.write.vpn, metasTLB.io.write.asid, metasTLB.io.write.mask, metasTLB.io.write.flag)
      printf("[TLBExec-"  + tlbname+ "]: data: wen:%d dest:%x ppn:%x pteaddr:%x\n", datasTLB.io.write.wen, datasTLB.io.write.dest, datasTLB.io.write.ppn, datasTLB.io.write.pteaddr)
      printf("[TLBExec-"  + tlbname+ "]: MemReq(%d, %d) MemResp(%d, %d) addr:%x cmd:%d rdata:%x cmd:%d\n", io.mem.req.valid, io.mem.req.ready, io.mem.resp.valid, io.mem.resp.ready, io.mem.req.bits.addr, io.mem.req.bits.cmd, io.mem.resp.bits.rdata, io.mem.resp.bits.cmd)
      printf("[TLBExec-"  + tlbname+ "]: io.ipf:%d hitinstrPF:%d missIPF:%d loadPF:%d storePF:%d\n", io.ipf, hitinstrPF, missIPF, io.pf.loadPF, io.pf.storePF)
    }
  }
}

object TLB {
  def apply(in: SimpleBusUC, mem: SimpleBusUC, flush: Bool, csrMMU: MMUIO)(implicit tlbConfig: TLBConfig) = {
    val tlb = Module(new TLB)
    tlb.io.in <> in
    tlb.io.mem <> mem
    tlb.io.flush := flush
    tlb.io.csrMMU <> csrMMU
    tlb
  }
}