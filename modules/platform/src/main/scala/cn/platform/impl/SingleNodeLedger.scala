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
      (current.utxo.nonEmpty, current.nonce.value, current.utxo)
    ):
      (acc, block) =>
        val addresses = current.addresses
        val (exists, nonce, previous) = acc

        val blockTransactions: List[Transaction] = block.transactions.toList

        val blockGreatestAddressNonce = blockTransactions.filter(_.is.exists(ti => addresses.exists(_ === ti.address))).map(_.nonce.value).maxOption.getOrElse(nonce)

        val blockAddressOutputs: Map[TransactionId, (TransactionOutput, Int)] = blockTransactions.flatMap:
          t =>
            val id = t.hash.value
            t.os.filter(i =>  addresses.exists(_ === i.address)).zipWithIndex.map((tOutput, idx) => TransactionId(s"$id-$idx") -> (tOutput, idx))
        .toMap

        val mergedUtxos = previous ++ blockAddressOutputs

        val usedIds = blockTransactions.flatMap(t => t.is.filter(i => addresses.exists(_ === i.address)).map(_.txId))

        val availableUtxos = mergedUtxos.filterNot((tid, _) => usedIds.exists(_ === tid))

        (exists ||  blockAddressOutputs.nonEmpty, blockGreatestAddressNonce,  availableUtxos)
    .compile
    .lastOrError
    .flatMap:
      (exists, nonce, mp) =>
        (if exists then IO.pure(TransactionData(current.addresses, Nonce(nonce + 1), mp)) else IO.raiseError(AddressNotFound))

  def appendTransaction(transaction: Transaction): IO[Unit] =
     mempool.offer(transaction).as(())


  def appendBlock(block: Block): IO[Either[Unit, Unit]] =
    IO.fromOption(block.prior)(BlockDereferenced).flatMap(prior =>
      st.flatModify:cur =>
        if cur == prior then
          (block, IO(Right(())))
        else
          (cur, IO(Left(())))
    )

  def process: Stream[IO, Unit] = Stream.fromQueueUnterminated(mempool).chunkLimit(4).parEvalMap(2):
    chunk =>
      current.flatMap:
        block =>
          val nowBlocks: Stream[IO, Block] = blocksUntil(block, 0)

          val validTransactions = chunk.toList.distinct.mapFilter:
            t =>
            val td = TransactionData.empty(t.is.map(_.address))
            val os = td.utxo.keys.toList


            val outputsAreAvailable = t.is.map(i => !os.contains(i.txId)).fold(true)(_ && _)

            if outputsAreAvailable then
              Some(t)
            else
              None

          if validTransactions.isEmpty then
            IO.println("There's no valid transactions")
          else
            IO.realTimeInstant
              .map(_.atOffset(ZoneOffset.UTC))
              .map(block.next(NonEmptyList.fromListUnsafe(validTransactions),_))
              .flatMap(b =>
                IO.fromOption(header.address.privateKey)(MissingPrivateKey)
                  .flatMap(signerService.sign(b.hash, _))
                  .map(sig => b.copy(signature = Some(sig)))
              ).flatMap(appendBlock(_)).void


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

  def mkGenesis: IO[Block] =
    IO.realTimeInstant.map:
      ins =>
        val amount = Amount(100)
        val trs = NonEmptyList.one(Transaction(Nonce(0), List.empty, List(TransactionOutput(amount, ledgerHeader.address.address))))
        new Block(None, trs, None,  ins.atOffset(ZoneOffset.UTC), 0)

  def apply(signerService: SignerService[IO]): Resource[IO, SingleNodeLedger] =
    Resource.make(
      Queue
        .bounded[IO, Transaction](32)
        .flatMap(
          mempool =>
            mkGenesis.flatMap(genesis =>  signerService.sign(genesis.hash, ledgerHeader.address.privateKey.get).map(s => genesis.sign(s)))
              .flatMap(g =>  Ref.of[IO, Block](g))
              .map(blockRef => new SingleNodeLedger(ledgerHeader, signerService, mempool, blockRef))
        )
  )(_ => IO.unit)
