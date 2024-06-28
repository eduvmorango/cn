package cn.platform.service

import cats.effect.IO
import cats.effect.kernel.Resource
import cn.core.api.TransactionOutput
import cn.core.api.TransactionRequest
import cn.core.shared.types.Amount
import cn.core.spi.Ledger
import cn.core.api.Address
import cn.core.api.EncodedPublicKey
import cn.core.api.EncodedPrivateKey
import java.time.ZoneOffset
import cats.effect.std.Random
import cats.syntax.all.*
import io.github.arainko.ducktape.*
import cn.core.api.TransactionException.InvalidAddressPublicKey
import cn.platform.iml.SingleNodeLedger
import cn.core.spi.Ledger.TransactionData
import cn.core.api.Block
import cn.core.shared.types.CnHash
import cn.core.api.TransactionId
import cn.core.spi.LedgerException.BlockNotFound
import cn.core.spi.LedgerException.TransactionNotFound
import cn.core.api.Transaction
import cn.core.api.UnsignedTransaction
import cn.core.api.TransactionException.EmptyTransactionRequest

case class Balance(available: Amount, utxos: List[TransactionOutput])

case class CreateTransaction(
    source: Address,
    destination: Address,
    amount: Amount,
    sourcePublicKey: EncodedPublicKey,
    sourcePrivateKey: EncodedPrivateKey
)

trait LedgerService[F[_]]:

  def getBalance(address: Address): F[Balance]

  def requestTransaction(request: CreateTransaction): F[Transaction]

  def requestTransactionBatch(batch: List[CreateTransaction]): F[Unit]

  def getBlocks: F[List[Block]]

  def getBlock(id: CnHash): F[Block]

  def getTransaction(txId: TransactionId): F[Transaction]

object LedgerService:

  def default(ledger: SingleNodeLedger, signerService: SignerService[IO]): Resource[IO, LedgerService[IO]] =
    Resource.eval(Random.scalaUtilRandom[IO]).map( rnd =>
      new LedgerService[IO]:

        def getBalance(address: Address): IO[Balance] =
          ledger
            .getTransactionData(ledger.blocks, TransactionData.empty(address))
            .map:
              td =>
                val utxos =  td.utxo.map(_._2._1).toList
                Balance(Amount.applyUnsafe(utxos.map(_.amount.value).sum), utxos)


        private def createSignedTransaction(request: CreateTransaction): IO[Transaction] =
          for
            _ <- signerService.checkPublicKeyAndAddress(request.source, request.sourcePublicKey).flatMap(if _ then IO.unit else IO.raiseError(InvalidAddressPublicKey))
            now <- IO.realTimeInstant.map(_.atOffset(ZoneOffset.UTC))
            tr = request.into[TransactionRequest].transform(Field.const(_.timestamp,now), Field.computed(_.outputs, r => Set(TransactionOutput(r.destination, r.amount))  ) )
            unsignedTransaction <- ledger
              .getTransactionData(ledger.blocks, TransactionData.empty(tr.source))
              .flatMap(td => IO.fromEither(tr.calculateTransaction(td.nonce, td.utxo)))
              signedTransaction <- signerService.sign(unsignedTransaction.hash, request.sourcePrivateKey).map(sig => unsignedTransaction.toTransaction(sig))
            _ <- signerService.checkSignature(signedTransaction, request.sourcePublicKey)
          yield signedTransaction

        private def sign(request: CreateTransaction, tr: UnsignedTransaction): IO[Transaction] =
          for
            _  <- signerService.checkPublicKeyAndAddress(request.source, request.sourcePublicKey).flatMap(r => IO.raiseWhen(!r)(InvalidAddressPublicKey))
            tr <- signerService.sign(tr.hash, request.sourcePrivateKey).map(tr.toTransaction)
            _  <- signerService.checkSignature(tr, request.sourcePublicKey)
          yield tr

        def requestTransaction(request: CreateTransaction): IO[Transaction] =
           createSignedTransaction(request).flatTap(ledger.appendTransaction)

        def requestTransactionBatch(batch: List[CreateTransaction]): IO[Unit] =
          val groupedBySource: Map[Address, List[CreateTransaction]] = batch.groupBy(t => t.source)
          val mergedRequests = groupedBySource
            .map:
              (source, ls) =>
                source -> ls.groupBy(_.destination).toList.map((_, lss) => lss.head.copy(amount = Amount.applyUnsafe(lss.map(_.amount.value.toDouble).sum)))

          IO.realTimeInstant.map(_.atOffset(ZoneOffset.UTC)).flatMap:
            now =>
              mergedRequests.toList.traverse:
                (source, requests) =>

                  val tr = TransactionRequest(source, requests.map(t => TransactionOutput(t.destination, t.amount)).toSet, now)
                  val blocks = ledger.blocks


                  IO.ref(TransactionData.empty(source)).flatMap:
                    ref =>
                      ref
                        .get
                        .flatMap(ledger.getTransactionData(blocks, _).flatTap(ref.set))
                        .flatMap(td => IO.fromEither(tr.calculateTransaction(td.nonce, td.utxo)))
                        .flatMap(sigTr => IO.fromOption(requests.headOption)(EmptyTransactionRequest).flatMap(sign(_, sigTr)))
              .flatMap(lss =>  ledger.appendTransactionBatch(lss))
              .void

        def getBlocks: IO[List[Block]] =
          ledger.blocks.compile.toList

        def getBlock(id: CnHash): IO[Block] = getBlocks.flatMap:
          ls => IO.fromOption(ls.find(_.hash === id))(BlockNotFound)

        def getTransaction(txId: TransactionId): IO[Transaction] =
          getBlocks.flatMap( ls => IO.fromOption(ls.flatMap(_.transactions.toList).find(t =>  t.hash.value === txId.value))(TransactionNotFound))

    )
