package cn.platform.http.model.r

import cn.core.api.Address
import cn.core.api.EncodedPrivateKey
import cn.core.api.EncodedPublicKey
import cn.core.api.TransactionOutput
import cn.core.shared.types.Amount
import cn.platform.http.model.codecs.json.given
import cn.platform.http.model.codecs.schema.given
import cn.platform.service.Balance
import cn.platform.service.CreateTransaction
import io.circe.Codec
import io.github.arainko.ducktape.*
import sttp.tapir.Schema

object balance:

  case class BalanceResponse(available: Amount, utxos: List[TransactionOutput]) derives Codec.AsObject, Schema

  case class CreateTransactionRequest(
    source: Address,
    destination: Address,
    amount: Amount,
    sourcePublicKey: EncodedPublicKey,
    sourcePrivateKey: EncodedPrivateKey
  ) derives Codec.AsObject,
      Schema:
    self =>

    def toDomain: CreateTransaction = self.to[CreateTransaction]

  object BalanceResponse:
    def fromDomain(balance: Balance): BalanceResponse = balance.to[BalanceResponse]
