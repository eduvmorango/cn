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

object LedgerService:

  def default(ledger: Ledger[IO], signerService: SignerService[IO]): Resource[IO, LedgerService[IO]] =
    Resource.eval(Random.scalaUtilRandom[IO]).map( rnd =>
      new LedgerService[IO]:

        def getBalance(address: Address): IO[Balance] =
          ledger
            .getAvailableUtxosForAddress(address)
            .map:
              mp =>
                val utxos = mp.map(_._2._1).toList
                Balance(Amount.applyUnsafe(utxos.map(_.amount.value).sum), utxos)


        def requestTransaction(request: CreateTransaction): IO[Unit] =
          for
            _ <- signerService.checkPublicKeyAndAddress(request.source, request.sourcePublicKey).flatMap(if _ then IO.unit else IO.raiseError(InvalidAddressPublicKey))
            now <- IO.realTimeInstant.map(_.atOffset(ZoneOffset.UTC))
            nonce <- rnd.nextIntBounded(9999).map(Nonce.apply)
            tr = request.into[TransactionRequest].transform(Field.const(_.nonce, nonce) ,Field.const(_.timestamp,now))
            transaction <- ledger.getAvailableUtxosForAddress(tr.source).flatMap(utxos => IO.fromEither(tr.calculateTransaction(utxos).leftMap(_ => InsufficientAmount)))
            signedTransaction <- signerService.sign(transaction.hash, request.sourcePrivateKey).map(SignedTransaction(_, transaction))
            _ <- signerService.checkSignature(signedTransaction, request.sourcePublicKey)
            _ <- ledger.appendTransaction(transaction)
          yield ()
    )
