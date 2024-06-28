package cn.platform.http.routes

import cats.effect.IO
import cats.syntax.all.*
import cn.platform.http.model.LedgerModel.*
import cn.platform.http.model.r.balance
import cn.platform.http.model.r.balance.BalanceResponse
import cn.platform.service.LedgerService
import sttp.tapir.server.http4s.Http4sServerInterpreter
import cn.platform.http.model.LedgerModel
import cn.core.api.CnException
import cn.platform.http.model.r.balance.BlockResponse
import cn.platform.http.model.r.balance.TransactionResponse

class LedgerRoutes(ledgerService: LedgerService[IO]):

  val getBalanceEndpoint =
    getBalance.serverLogic:
      address =>
        ledgerService.getBalance(address).map(BalanceResponse.fromDomain(_))
        .attemptNarrow[CnException]
        .map:
          case Right(r) => r.asRight
          case Left(l) => LedgerModel.handleError(l).asLeft

  val getTransactionEndpoint =
    getTransaction.serverLogic:
      txId =>
        ledgerService.getTransaction(txId).map(TransactionResponse.fromDomain(_))
        .attemptNarrow[CnException]
        .map:
          case Right(r) => r.asRight
          case Left(l) => LedgerModel.handleError(l).asLeft

  val getBlockEndpoint =
    getBlock.serverLogic:
      id =>
        ledgerService.getBlock(id).map(BlockResponse.fromDomain(_))
        .attemptNarrow[CnException]
        .map:
          case Right(r) => r.asRight
          case Left(l) => LedgerModel.handleError(l).asLeft




  val postRequestTransactionEndpoint =
    postRequestTransaction.serverLogic:
      req =>

        ledgerService.requestTransaction(req.toDomain)
          .attemptNarrow[CnException]
        .map:
          case Right(_) => ().asRight
          case Left(l) => LedgerModel.handleError(l).asLeft


  val postRequestTransactionBatchEndpoint =
    postRequestTransactionBatch.serverLogic:
      req =>
        ledgerService.requestTransactionBatch(req.map(_.toDomain))
          .attemptNarrow[CnException]
        .map:
          case Right(_) => ().asRight
          case Left(l) => LedgerModel.handleError(l).asLeft


  val getBlocksEndpoint = getBlocks.serverLogic:
    _ =>
      ledgerService.getBlocks.map(_.map(BlockResponse.fromDomain).asRight)



  val endpoints = List(getBalanceEndpoint, postRequestTransactionEndpoint, getBlocksEndpoint, getBlockEndpoint, getTransactionEndpoint,  postRequestTransactionBatchEndpoint)

  val routes = Http4sServerInterpreter[IO]().toRoutes(endpoints)
