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
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.*

object json:

  export io.github.iltotore.iron.circe.given

  private val string: Codec[String] = Codec.from(Decoder.decodeString, Encoder.encodeString)
  private val double: Codec[Double] = Codec.from(Decoder.decodeDouble, Encoder.encodeDouble)
  private val int: Codec[Int]       = Codec.from(Decoder.decodeInt, Encoder.encodeInt)

  given Codec[EncodedPublicKey]  = string.imap(EncodedPublicKey.apply(_))(_.value)
  given Codec[EncodedPrivateKey] = string.imap(EncodedPrivateKey.apply(_))(_.value)
  given Codec[Address]           = string.imap(Address.apply(_))(_.value)
  given Codec[Signature]         = string.imap(Signature.apply(_))(_.value)
  given Codec[CnHash]            = string.imap(CnHash.apply(_))(_.value)
  given Codec[TransactionId]     = string.imap(TransactionId.apply(_))(_.value)
  given Codec[Nonce]             = int.imap(Nonce.apply(_))(_.value)

  given Codec[TransactionInput]  = deriveCodec
  given Codec[TransactionOutput] = deriveCodec
  given Codec[Transaction]       = deriveCodec
