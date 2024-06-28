package cn.platform.service

import cats.effect.IO
import cats.effect.kernel.Resource
import cn.platform.iml.SingleNodeLedger
import weaver.IOSuite
import cn.core.shared.types.Amount
import cats.effect.syntax.all.*
import fs2.Stream
import scala.concurrent.duration.*
import cats.syntax.all.*
import io.github.iltotore.iron.autoCastIron
import io.circe.syntax.*
import cn.platform.http.model.codecs.json.given
import cn.platform.http.model.r.balance.BlockResponse

object LedgerServiceSuite extends IOSuite:

  override type Res = (SignerService[IO], AddressService[IO], LedgerService[IO], SingleNodeLedger)

  val mkResource = SignerService
      .default
      .flatMap(sign =>
        AddressService(sign).flatMap(address =>
          SingleNodeLedger(sign).flatMap(ledger =>
              LedgerService.default(ledger, sign).map(l => (sign, address, l, ledger)
          )
        )
      )
    )

  override def sharedResource: Resource[IO, (SignerService[IO], AddressService[IO], LedgerService[IO], SingleNodeLedger)] = mkResource

  test("Request a valid transaction"):
    mkResource.use:
      (_, address, ledgerService, ledger) =>
      address.createAddress.flatMap:
        destination =>
          val sourceAddress = ledger.header.address
          val value = 10D
          val ct = CreateTransaction(sourceAddress.address, destination.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get)

          for
            initialBalance <-  ledgerService.getBalance(sourceAddress.address)
            _ <- ledgerService.requestTransaction(ct)
            f  <- ledger.process.compile.drain.start
            _  <- IO.sleep(100.milli)
            sourceBalance <- ledgerService.getBalance(sourceAddress.address)
            destinationBalance <- ledgerService.getBalance(destination.address)
            _  <- f.cancel
          yield expect((initialBalance.available.value - value) === sourceBalance.available.value && destinationBalance.available.value.toDouble === value)

  test("Ignore duplicated transaction:".only):
    mkResource.use:
      (_, address, ledgerService, ledger) =>
      address.createAddress.flatMap:
        destination =>
          val sourceAddress = ledger.header.address
          val value = 10D
          val ct = CreateTransaction(sourceAddress.address, destination.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get)

          for
            initialBalance <-  ledgerService.getBalance(sourceAddress.address)
            tr <- ledgerService.requestTransaction(ct)
            f  <- ledger.process.compile.drain.start
            _  <- IO.sleep(100.milli)
            _  <- ledger.appendTransaction(tr)
            _  <- IO.sleep(100.milli)
            sourceBalance <- ledgerService.getBalance(sourceAddress.address)
            destinationBalance <- ledgerService.getBalance(destination.address)
            _  <- f.cancel
            _ <- ledgerService.getBlocks.flatMap(xz => IO.println(xz.map(BlockResponse.fromDomain).asJson) )

          yield expect((initialBalance.available.value - value) === sourceBalance.available.value && destinationBalance.available.value.toDouble === value)
