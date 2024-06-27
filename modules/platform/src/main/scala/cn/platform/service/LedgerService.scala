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
import cn.core.api.Nonce
import cats.syntax.all.*
import io.github.arainko.ducktape.*
import cn.core.api.TransactionException.InsufficientAmount
import cn.core.api.SignedTransaction
import cn.core.api.TransactionException.InvalidAddressPublicKey
import cn.platform.iml.SingleNodeLedger
import cn.core.spi.Ledger.TransactionData
import cn.core.api.Block

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

  def requestTransaction(request: CreateTransaction): F[Unit]

  def getBlocks: F[List[Block]]

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


        def requestTransaction(request: CreateTransaction): IO[Unit] =
          for
            _ <- signerService.checkPublicKeyAndAddress(request.source, request.sourcePublicKey).flatMap(if _ then IO.unit else IO.raiseError(InvalidAddressPublicKey))
            now <- IO.realTimeInstant.map(_.atOffset(ZoneOffset.UTC))
            tr = request.into[TransactionRequest].transform(Field.const(_.timestamp,now))
            transaction <- ledger
              .getTransactionData(ledger.blocks, TransactionData.empty(tr.source))
              .flatMap(td => IO.fromEither(tr.calculateTransaction(td.nonce, td.utxo).leftMap(_ => InsufficientAmount)))
            signedTransaction <- signerService.sign(transaction.hash, request.sourcePrivateKey).map(SignedTransaction(_, transaction))
            _ <- signerService.checkSignature(signedTransaction, request.sourcePublicKey)
            _ <- ledger.appendTransaction(transaction)
          yield ()

        def getBlocks: IO[List[Block]] =
          ledger.blocks.compile.toList
    )
