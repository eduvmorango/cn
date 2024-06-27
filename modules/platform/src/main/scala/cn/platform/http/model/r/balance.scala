package cn.platform.http.model.r

import cats.data.NonEmptyList
import cn.core.api.Address
import cn.core.api.Block
import cn.core.api.EncodedPrivateKey
import cn.core.api.EncodedPublicKey
import cn.core.api.Nonce
import cn.core.api.Signature
import cn.core.api.Transaction
import cn.core.api.TransactionInput
import cn.core.api.TransactionOutput
import cn.core.shared.types.Amount
import cn.core.shared.types.CnHash
import cn.platform.http.model.codecs.json.given
import cn.platform.http.model.codecs.schema.given
import cn.platform.service.Balance
import cn.platform.service.CreateTransaction
import io.circe.Codec
import io.github.arainko.ducktape.*
import java.time.OffsetDateTime
import sttp.tapir.Schema

object balance:

  case class BalanceResponse(available: Amount, utxos: List[TransactionOutput]) derives Codec.AsObject, Schema

  case class TransactionResponse(id: CnHash, nonce: Nonce, is: List[TransactionInput], os: List[TransactionOutput])
    derives Codec.AsObject, Schema

  object TransactionResponse:

    def fromDomain(t: Transaction): TransactionResponse =
      t.into[TransactionResponse].transform(Field.computed(_.id, _.hash))

  case class BlockResponse(
    prior: CnHash,
    transactions: List[TransactionResponse],
    signature: Signature,
    timestamp: OffsetDateTime,
    index: Int
  ) derives Codec.AsObject,
      Schema

  object BlockResponse:

    def fromDomain(block: Block): BlockResponse =
      block
        .into[BlockResponse]
        .transform(
          Field.computed(_.prior, _.prior.map(_.hash).getOrElse(CnHash(""))),
          Field.computed(_.signature, _.signature.get),
          Field.computed(_.transactions, _.transactions.toList.map(TransactionResponse.fromDomain))
        )

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
