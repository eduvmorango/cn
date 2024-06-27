package cn.platform.http.model

import cn.core.api.Address
import cn.core.api.CnException
import cn.core.api.TransactionException
import cn.core.spi.LedgerException
import cn.platform.http.HttpException
import cn.platform.http.model.codecs.codec.given
import cn.platform.http.model.r.balance.*
import cn.platform.service.SignerException
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

object LedgerModel:

  def handleError(t: CnException): HttpException =
    t match
    case LedgerException.AddressNotFound              => HttpException.UnprocessableEntity("Ledger not found")
    case TransactionException.InsufficientAmount      => HttpException.UnprocessableEntity("Insufficient Amount")
    case TransactionException.InvalidSignature        => HttpException.UnprocessableEntity("Invalid Signature")
    case TransactionException.InvalidAddressPublicKey =>
      HttpException.UnprocessableEntity("PublicKey doesn't match Address")
    case SignerException.SigningError                 => HttpException.BadRequest("Signign error")
    case e                                            => HttpException.InternalServerError()

  val getBalance = endpoint
    .get
    .in("ledger" / "balance" / path[Address]("address"))
    .out(jsonBody[BalanceResponse])
    .errorOut(HttpException.errorOut)

  val getBlocks = endpoint
    .get
    .in("ledger" / "blocks")
    .out(jsonBody[List[BlockResponse]])
    .errorOut(HttpException.errorOut)

  val postRequestTransaction =
    endpoint
      .post
      .in("ledger" / "transaction")
      .in(jsonBody[CreateTransactionRequest])
      .out(statusCode(StatusCode.Accepted))
      .errorOut(HttpException.errorOut)
