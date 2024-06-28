package cn.platform.iml

import _root_.cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cn.core.api.Address
import cn.core.api.Block
import cn.core.api.EncodedPrivateKey
import cn.core.api.EncodedPublicKey
import cn.core.api.LedgerHeader
import cn.core.api.Transaction
import cn.core.api.TransactionOutput
import cn.core.shared.types.*
import cn.core.spi.Ledger
import cn.platform.service.SignerService
import fs2.Stream
import io.github.iltotore.iron.*
import cn.core.api.Nonce
import java.time.ZoneOffset
import cn.core.api.TransactionId
import _root_.cats.syntax.all.*
import io.github.iltotore.iron.constraint.numeric.Greater
import cn.core.api.AddressKeyPair
import cn.core.spi.LedgerException.AddressNotFound
import cn.core.spi.Ledger.TransactionData
import cn.core.spi.LedgerException.MissingPrivateKey
import cn.core.spi.LedgerException.BlockDereferenced
import cn.core.api.UnsignedTransaction
import fs2.Chunk

class SingleNodeLedger(
  val header: LedgerHeader,
  signerService: SignerService[IO],
  mempool: Queue[IO, Transaction],
  st: Ref[IO, Block]
) extends Ledger[IO]:

  def current: IO[Block] = st.get

  def blocks: Stream[IO, Block] = Stream.eval(current).flatMap(blocksUntil(_, 0))

  def blocksUntil(block: Block, idx: Int): Stream[IO, Block] =
      if block.index == idx then
        Stream(block)
      else if idx < 0 || idx > block.index  then
        Stream.raiseError(Throwable("Invalid Index"))
      else
        Stream.iterable(block.flattenUntil(idx))

  def getTransactionData(bs: Stream[IO, Block], current: TransactionData): IO[TransactionData] =
    bs.fold(
      (current.utxo.nonEmpty, current.nonce, current.utxo, current.accNewOutputs)
    ):
      (acc, block) =>
        val addresses = current.addresses
        val (exists, nonces, previous, accNewOutputs) = acc

        val blockTransactions: List[Transaction] = block.transactions.toList

        val newNonces: Map[Address.OpaqueType, List[Int]] = blockTransactions.filter(_.is.exists(ti => addresses.exists(_ === ti.address))).map(
          t => t.is.head.address -> t.nonce.value).groupBy(_._1).map((k,v) => k -> v.map(_._2))

        val mergedNonces = ((nonces.view.mapValues(n => List(n.value)).toMap) ++ newNonces).view.mapValues(l =>  Nonce(l.max)).toMap


        val blockAddressOutputs: Map[TransactionId, (TransactionOutput, Int)] = blockTransactions.flatMap:
          t =>
            val id = t.hash.value
            t.os.zipWithIndex.map((tOutput, idx) =>  TransactionId(s"$id-$idx") -> (tOutput, idx)).filter((_, o) =>  addresses.exists(_ === o._1.address))
        .toMap

        val mergedUtxos = previous ++ blockAddressOutputs

        val usedIds = blockTransactions.flatMap(t => t.is.filter(i => addresses.exists(_ === i.address)).map(_.txId))

        val availableUtxos = mergedUtxos.filterNot((tid, _) => usedIds.exists(_ === tid))


        (exists ||  blockAddressOutputs.nonEmpty,  mergedNonces,  availableUtxos, accNewOutputs)
    .compile
    .lastOrError
    .flatMap:
      (exists, nonces, mp, newOutputs) =>
        val noncesUpdated =
          nonces.updatedWith(current.addresses.head)(n => n.map(v => Nonce(v.value + 1)))
        (if exists then IO.pure(TransactionData(current.addresses, noncesUpdated, mp, newOutputs)) else IO.raiseError(AddressNotFound))

  def appendTransaction(transaction: Transaction): IO[Unit] =
     mempool.offer(transaction).void

  def appendTransactionBatch(batch: List[Transaction]): IO[Unit] =
     mempool.tryOfferN(batch).void

  def appendBlock(block: Block): IO[Either[Unit, Unit]] =
    IO.fromOption(block.prior)(BlockDereferenced).flatMap(prior =>
      st.flatModify:cur =>
        if cur == prior then
          (block, IO(Right(())))
        else
          (cur, IO(Left(())))
    )

  def process: Stream[IO, Unit] =
    def process0(chunk: Chunk[Transaction], retries: Int): Stream[IO, Unit] =
      if retries <= 0 then
        Stream.unit
      else
        Stream.eval(current)
        .flatMap:
          block =>
            val nowBlocks: Stream[IO, Block] = blocksUntil(block, 0)
              val iss = chunk.toList.flatMap(_.is).map(_.address)

              Stream.eval(getTransactionData(nowBlocks, TransactionData.empty(iss))).flatMap:
                itd =>


                val vTransactions =
                  chunk.toList.distinct.foldLeft[(TransactionData, List[Transaction])]((itd, List.empty)):
                    (tdts, t) =>
                      val (td, ts) = tdts

                        val availablesUtxos = td.utxo.keys.toList

                        val usedOutputs = t.is.filter(i => availablesUtxos.contains(i.txId))

                        if usedOutputs == t.is  then
                         (td.filterUtxo(t.is.map(_.txId)) , t :: ts)
                        else
                          (td, ts)
                  ._2.reverse

                if vTransactions.isEmpty then
                  Stream.eval(IO.unit) //IO.println("There's no valid transactions"))
                else
                  Stream.eval(
                    IO.realTimeInstant
                      .map(_.atOffset(ZoneOffset.UTC))
                      .map(block.next(NonEmptyList.fromListUnsafe(vTransactions),_))
                      .flatMap(b =>
                        IO.fromOption(header.address.privateKey)(MissingPrivateKey)
                          .flatMap(signerService.sign(b.hash, _))
                          .map(sig => b.copy(signature = Some(sig)))
                      .flatMap(appendBlock(_)))
                  )
                  .flatMap:
                    case Left(_) => process0(chunk, retries - 1)
                    case _ => Stream.eval(IO.unit) // IO.println(s"Block added on $retries"))

    Stream.fromQueueUnterminated(mempool).chunkLimit(4).parEvalMap(2)(process0(_, 3).compile.drain)

object SingleNodeLedger:

  val ledgerHeader = LedgerHeader(
    AddressKeyPair(
      Address("14u6MV6mccasLvGBPw5BtVeXWcQ7Ej7mhJ"),
      EncodedPublicKey(
        "MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAE2947SptyZHxhN3s1eAiteJxK6MDW0K3FfNXvipLNxpk78FJKYqTU6Tg49b6vuIdDaJ29TSjK9H88drZCnGnVsw=="
      ),
      Some(
        EncodedPrivateKey(
          "MIGNAgEAMBAGByqGSM49AgEGBSuBBAAKBHYwdAIBAQQg+H7iMneZTTUQDT4rRmnd3IgwtGqpheekRBf4MpiOA9KgBwYFK4EEAAqhRANCAATb3jtKm3JkfGE3ezV4CK14nErowNbQrcV81e+Kks3GmTvwUkpipNTpODj1vq+4h0Nonb1NKMr0fzx2tkKcadWz"
        )
      )
    )
  )

  def mkGenesis(signService: SignerService[IO]): IO[Block] =
    IO.realTimeInstant.flatMap:
      ins =>
        val tr =  UnsignedTransaction(Nonce(0), List.empty, List(TransactionOutput(ledgerHeader.address.address, Amount(100))))

        IO.fromOption(ledgerHeader.address.privateKey)(MissingPrivateKey)
          .flatMap(pk => signService.sign(tr.hash, pk).map(tr.toTransaction))
          .map(t =>  new Block(None, NonEmptyList.one(t), None,  ins.atOffset(ZoneOffset.UTC), 0))


  def apply(signerService: SignerService[IO]): Resource[IO, SingleNodeLedger] =
    Resource.make(
      Queue
        .bounded[IO, Transaction](32)
        .flatMap(
          mempool =>
            mkGenesis(signerService).flatMap(genesis =>  signerService.sign(genesis.hash, ledgerHeader.address.privateKey.get).map(s => genesis.sign(s)))
              .flatMap(g =>  Ref.of[IO, Block](g))
              .map(blockRef => new SingleNodeLedger(ledgerHeader, signerService, mempool, blockRef))
        )
  )(_ => IO.unit)
