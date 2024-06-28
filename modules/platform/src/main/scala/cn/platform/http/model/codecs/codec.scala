package cn.platform.http.model.codecs

import cn.core.api.Address
import cn.core.api.EncodedPrivateKey
import cn.core.api.EncodedPublicKey
import cn.core.api.TransactionId
import cn.core.shared.types.CnHash
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain

object codec:

  given Codec[String, Address, TextPlain]           = Codec.string.map(Address.apply(_))(_.value)
  given Codec[String, EncodedPublicKey, TextPlain]  = Codec.string.map(EncodedPublicKey.apply(_))(_.value)
  given Codec[String, EncodedPrivateKey, TextPlain] = Codec.string.map(EncodedPrivateKey.apply(_))(_.value)
  given Codec[String, CnHash, TextPlain]            = Codec.string.map(CnHash.apply(_))(_.value)
  given Codec[String, TransactionId, TextPlain]     = Codec.string.map(TransactionId.apply(_))(_.value)
