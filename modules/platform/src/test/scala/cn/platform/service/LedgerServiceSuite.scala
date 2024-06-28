package cn.platform.service

import cats.effect.IO
import cats.effect.kernel.Resource
import cn.platform.iml.SingleNodeLedger
import weaver.IOSuite
import cn.core.shared.types.Amount
import cats.effect.syntax.all.*
import scala.concurrent.duration.*
import cats.syntax.all.*
import cn.core.api.TransactionException.InsufficientAmount
import cn.core.api.CnException
import cn.core.spi.LedgerException.AddressNotFound

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

  test("Sum two utxos to reach requested amount into a valid transaction"):
    mkResource.use:
      (_, address, ledgerService, ledger) =>
      (address.createAddress, address.createAddress).flatMapN:
        (d1, d2) =>
          val sourceAddress = ledger.header.address
          val value = 10D
          val ct = CreateTransaction(sourceAddress.address, d1.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get)

          val ct2 = CreateTransaction(d1.address, d2.address, Amount.applyUnsafe(20), d1.publicKey, d1.privateKey.get)


          for
            initialBalance <-  ledgerService.getBalance(sourceAddress.address)
            f  <- ledger.process.compile.drain.start
            _  <- IO.sleep(100.milli)
            _ <- ledgerService.requestTransaction(ct)
            _  <- IO.sleep(100.milli)
            _ <- ledgerService.requestTransaction(ct)
            _  <- IO.sleep(100.milli)
            _ <- ledgerService.requestTransaction(ct2)
            _  <- IO.sleep(100.milli)
            b  <- ledger.current

          yield expect(b.transactions.toList.flatMap(_.is).size == 2 )

  test("When the amount equals the uxto doesn't generate an adjustment output "):
    mkResource.use:
      (_, address, ledgerService, ledger) =>
      address.createAddress.flatMap:
        destination =>
          val sourceAddress = ledger.header.address
          val value = 100D
          val ct = CreateTransaction(sourceAddress.address, destination.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get)

          for
            _ <- ledgerService.requestTransaction(ct)
            f  <- ledger.process.compile.drain.start
            _  <- IO.sleep(100.milli)
            sourceBalance <- ledgerService.getBalance(sourceAddress.address)
            destinationBalance <- ledgerService.getBalance(destination.address)
            _  <- f.cancel
            b <- ledger.current
          yield expect(b.transactions.toList.flatMap(_.os).size == 1)

  test("When the source doesn't have enough balance the transaction should be denied"):
    mkResource.use:
      (_, address, ledgerService, ledger) =>
      address.createAddress.flatMap:
        destination =>
          val sourceAddress = ledger.header.address
          val value = 110D
          val ct = CreateTransaction(sourceAddress.address, destination.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get)

          ledgerService.requestTransaction(ct).attemptNarrow[CnException].map:
            case Left(e) if e == InsufficientAmount => true
            case _ => false
          .map(expect(_))

  test("Ignore duplicated transaction:"):
    mkResource.use:
      (_, address, ledgerService, ledger) =>
      address.createAddress.flatMap:
        destination =>
          val sourceAddress = ledger.header.address
          val value = 10D
          val ct = CreateTransaction(sourceAddress.address, destination.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get)

          for
            tr <- ledgerService.requestTransaction(ct)
            f  <- ledger.process.compile.drain.start
            _  <- IO.sleep(100.milli)
            _  <- ledger.appendTransaction(tr)
            _  <- IO.sleep(100.milli)
            sourceBalance <- ledgerService.getBalance(sourceAddress.address)
            destinationBalance <- ledgerService.getBalance(destination.address)
            _  <- f.cancel
            blocks <- ledgerService.getBlocks
          yield  expect(blocks.size == 2)


  test("Batched transactions from the same source address are merged into one"):
    mkResource.use:
      (_, address, ledgerService, ledger) =>
      address.createAddress.replicateA(5).flatMap:
        (ds)=>
          val sourceAddress = ledger.header.address
          val value = 10
          val ct =
            ds.map(d => CreateTransaction(sourceAddress.address, d.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get))

          for
            _ <- ledgerService.requestTransactionBatch(ct)
            f  <- ledger.process.compile.drain.start
            _  <- IO.sleep(100.milli)
            _  <- f.cancel
            b <- ledger.current
          // 100 -> 10,10,10,10,10, 50
          yield expect(b.transactions.toList.size == 1 && b.transactions.toList.flatMap(_.os).size == 6)

  test("Batched transactions are ignored if there's errors "):
    mkResource.use:
      (_, address, ledgerService, ledger) =>
      address.createAddress.replicateA(5).flatMap:
        (ds) =>
          val sourceAddress = ledger.header.address
          val value = 10
          val ct =
           CreateTransaction(ds(0).address, ds(1).address, Amount.applyUnsafe(value), ds(0).publicKey, ds(0).privateKey.get) ::  ds.map(d => CreateTransaction(sourceAddress.address, d.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get))

          // ds(0) isn't initialized
          ledgerService.requestTransactionBatch(ct).attemptNarrow[CnException].map:
            case Left(e) if e == AddressNotFound => true
            case _ => false
          .map(expect(_))


  test("Batches can generate multiple blocks in arbitrary orders if they're consistent"):
    mkResource.use:
      (_, address, ledgerService, ledger) =>
      address.createAddress.replicateA(5).flatMap:
        (ds) =>
          val sourceAddress = ledger.header.address
          val value = 10
          val startCt =
             ds.map(d => CreateTransaction(sourceAddress.address, d.address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get))


          val nextBatch = List(
            CreateTransaction(sourceAddress.address, ds(0).address, Amount.applyUnsafe(value), sourceAddress.publicKey, sourceAddress.privateKey.get),
            CreateTransaction(ds(0).address, ds(1).address, Amount.applyUnsafe(value), ds(0).publicKey, ds(0).privateKey.get),
            CreateTransaction(ds(1).address, ds(2).address, Amount.applyUnsafe(value), ds(1).publicKey, ds(1).privateKey.get),
            CreateTransaction(ds(2).address, ds(3).address, Amount.applyUnsafe(value), ds(2).publicKey, ds(2).privateKey.get),
            CreateTransaction(ds(3).address, ds(4).address, Amount.applyUnsafe(value), ds(3).publicKey, ds(3).privateKey.get),
            CreateTransaction(ds(4).address, sourceAddress.address, Amount.applyUnsafe(value), ds(4).publicKey, ds(4).privateKey.get),
          )
          for
            _ <- ledgerService.requestTransactionBatch(startCt)
            f  <- ledger.process.compile.drain.start
            _  <- IO.sleep(200.milli)
            _ <- ledgerService.requestTransactionBatch(nextBatch)
            _ <- IO.sleep(500.milli)
            _  <- f.cancel
            b <- ledgerService.getBlocks
            blockSize = b.size
            utxoSize = 9

          yield expect(blockSize == 4  && utxoSize == b.flatMap(_.transactions.toList).flatMap(_.os).toSet.size)
