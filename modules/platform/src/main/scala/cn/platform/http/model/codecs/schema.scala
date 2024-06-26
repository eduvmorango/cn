package cn.platform.http.model.codecs

import cats.syntax.all.*
import cn.core.api.Address
import cn.core.api.EncodedPrivateKey
import cn.core.api.EncodedPublicKey
import cn.core.api.TransactionOutput
import cn.core.shared.types.Amount
import io.github.iltotore.iron.*
import sttp.tapir.Schema

object schema:

  given Schema[Amount]            = Schema.schemaForDouble.map(Amount.option)(_.value)
  given Schema[EncodedPublicKey]  = Schema.schemaForString.map(EncodedPublicKey.apply(_).some)(_.value)
  given Schema[EncodedPrivateKey] = Schema.schemaForString.map(EncodedPrivateKey.apply(_).some)(_.value)
  given Schema[Address]           = Schema.schemaForString.map(Address.apply(_).some)(_.value)

  given Schema[TransactionOutput] = Schema.derived
