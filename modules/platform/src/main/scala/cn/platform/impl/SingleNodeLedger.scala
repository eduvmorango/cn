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

class SingleNodeLedger(
  val header: LedgerHeader,
  signerService: SignerService[IO],
  mempool: Queue[IO, Transaction],
  st: Ref[IO, Block]
) extends Ledger[IO]:

  def current: IO[Block] = st.get

  def blocks: Stream[IO, Block] =
    Stream.eval(current).flatMap(c => Stream.iterable(c.flatten))

  def getAvailableUtxosForAddress(bs: Stream[IO, Block], address: Address): IO[Map[TransactionId, (TransactionOutput, Int)]] =
      bs.fold((false, Map.empty[TransactionId, (TransactionOutput, Int)]))(
        (acc, cur) =>
          val (exists, available) = acc

          val outputs: Map[TransactionId, (TransactionOutput, Int)] = cur.transactions.toList.flatMap:
            t =>
              val id = t.hash.value
              t.os.filter(_.address === address).zipWithIndex.map((to, i) =>  TransactionId(s"$id-$i") -> (to, i))
          .toMap

          val utxos: Map[TransactionId, (TransactionOutput, Int)] = available ++ outputs

          val ued: List[TransactionId] = cur.transactions.toList.flatMap(_.is.filter(_.address === address).map(_.txId))

          (exists || outputs.nonEmpty, utxos.filterNot((tid, _) =>  ued.exists(_ === tid)))

          )
          .compile
          .lastOrError
          .flatMap:
            (exists, mp) =>
              if exists then IO(mp) else IO.raiseError(AddressNotFound)

  def getUtxosForAddress(address: Address): IO[List[TransactionOutput]] =
    blocks.fold(List.empty[TransactionOutput])(
      (av, cur) =>

      av ++ cur.transactions.toList.flatMap:
        t =>
          t.os.filter(_.address === address)


    ).compile.lastOrError

  def appendTransaction(transaction: Transaction): IO[Unit] =
    IO.println(transaction) *> mempool.offer(transaction).as(())




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
        val trs = NonEmptyList.one(Transaction(List.empty, List(TransactionOutput(amount, ledgerHeader.address.address))))
        new Block(None, trs, None, Nonce(0), ins.atOffset(ZoneOffset.UTC), 0)

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
