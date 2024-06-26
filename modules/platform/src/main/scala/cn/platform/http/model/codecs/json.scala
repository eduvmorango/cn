package cn.platform.http.model.codecs

import cats.syntax.all.*
import cn.core.api.Address
import cn.core.api.EncodedPrivateKey
import cn.core.api.EncodedPublicKey
import cn.core.api.TransactionOutput
import cn.core.shared.types.Amount
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.*

object json:

  export io.github.iltotore.iron.circe.given

  private val string: Codec[String] = Codec.from(Decoder.decodeString, Encoder.encodeString)
  private val double: Codec[Double] = Codec.from(Decoder.decodeDouble, Encoder.encodeDouble)

  given Codec[EncodedPublicKey]  = string.imap(EncodedPublicKey.apply(_))(_.value)
  given Codec[EncodedPrivateKey] = string.imap(EncodedPrivateKey.apply(_))(_.value)
  given Codec[Address]           = string.imap(Address.apply(_))(_.value)

  given Codec[TransactionOutput] = deriveCodec
