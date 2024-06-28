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

object LedgerServiceSuite extends IOSuite:

  override type Res = (SignerService[IO], AddressService[IO], LedgerService[IO], SingleNodeLedger)

  override def sharedResource: Resource[IO, (SignerService[IO], AddressService[IO], LedgerService[IO], SingleNodeLedger)] =
    SignerService
      .default
      .flatMap(sign =>
        AddressService(sign).flatMap(address =>
          SingleNodeLedger(sign).flatMap(ledger =>
              LedgerService.default(ledger, sign).map(l => (sign, address, l, ledger)
          )
        )
      )
    )



  test("Request a valid transaction"):
    (s) =>
      val (_, address, ledgerService, ledger) = s
      address.createAddress.flatMap:
        destination =>
          val sourceAddress = ledger.header.address

          val value = 10d

          val ct = CreateTransaction(sourceAddress.address, destination.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get)

          for
            initialBalance <-  ledgerService.getBalance(sourceAddress.address)
            req <- ledgerService.requestTransaction(ct)
            f  <- ledger.process.compile.drain.start
            _  <- IO.sleep(1.second)
            sourceBalance <- ledgerService.getBalance(sourceAddress.address)
            destinationBalance <- ledgerService.getBalance(destination.address)
            _  <- f.cancel
          yield expect((initialBalance.available.value - value) === sourceBalance.available.value && destinationBalance.available.value.toDouble === value)
