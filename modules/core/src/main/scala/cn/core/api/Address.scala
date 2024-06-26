package cn.core.api

import cn.core.shared.Opaque

object Address extends Opaque[String]
type Address = Address.OpaqueType

case class KeyPair(publicKey: EncodedPublicKey, privateKey: Option[EncodedPrivateKey])

case class AddressKeyPair(address: Address, publicKey: EncodedPublicKey, privateKey: Option[EncodedPrivateKey])

//object Address:
// given Hash[Address] = Hash.by(_.publicKey.toString)
