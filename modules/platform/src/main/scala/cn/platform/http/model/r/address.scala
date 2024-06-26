package cn.platform.http.model.r

import cn.core.api.Address
import cn.core.api.AddressKeyPair
import cn.core.api.EncodedPrivateKey
import cn.core.api.EncodedPublicKey
import cn.platform.http.model.codecs.json.given
import cn.platform.http.model.codecs.schema.given
import io.circe.Codec
import io.github.arainko.ducktape.*
import sttp.tapir.Schema

object address:

  case class CreatedAddressResponse(
    address: Address,
    publicKey: EncodedPublicKey,
    privateKey: Option[EncodedPrivateKey]
  ) derives Codec.AsObject,
      Schema

  object CreatedAddressResponse:
    def fromDomain(address: AddressKeyPair): CreatedAddressResponse = address.to[CreatedAddressResponse]
