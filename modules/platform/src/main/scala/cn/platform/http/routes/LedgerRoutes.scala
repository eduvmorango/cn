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

class LedgerRoutes(ledgerService: LedgerService[IO]):

  val getBalanceEndpoint =
    getBalance.serverLogic:
      publicKey =>
        ledgerService.getBalance(publicKey).map(BalanceResponse.fromDomain(_))
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



  val endpoints = List(getBalanceEndpoint, postRequestTransactionEndpoint)

  val routes = Http4sServerInterpreter[IO]().toRoutes(endpoints)
