package cn.platform.http.model.codecs

import cats.syntax.all.*
import cn.core.api.Address
import cn.core.api.EncodedPrivateKey
import cn.core.api.EncodedPublicKey
import cn.core.api.Nonce
import cn.core.api.Signature
import cn.core.api.Transaction
import cn.core.api.TransactionId
import cn.core.api.TransactionInput
import cn.core.api.TransactionOutput
import cn.core.shared.types.Amount
import cn.core.shared.types.CnHash
import io.github.iltotore.iron.*
import sttp.tapir.Schema

object schema:

  given Schema[Amount]            = Schema.schemaForDouble.map(Amount.option)(_.value)
  given Schema[EncodedPublicKey]  = Schema.schemaForString.map(EncodedPublicKey.apply(_).some)(_.value)
  given Schema[EncodedPrivateKey] = Schema.schemaForString.map(EncodedPrivateKey.apply(_).some)(_.value)
  given Schema[Address]           = Schema.schemaForString.map(Address.apply(_).some)(_.value)

  given Schema[Nonce]         = Schema.schemaForInt.map(Nonce.apply(_).some)(_.value)
  given Schema[CnHash]        = Schema.schemaForString.map(CnHash.apply(_).some)(_.value)
  given Schema[TransactionId] = Schema.schemaForString.map(TransactionId.apply(_).some)(_.value)

  given Schema[Signature] = Schema.schemaForString.map(Signature.apply(_).some)(_.value)

  given Schema[TransactionInput]  = Schema.derived
  given Schema[TransactionOutput] = Schema.derived
  given Schema[Transaction]       = Schema.derived
