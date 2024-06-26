package cn.core.api

import cn.core.shared.Opaque
object EncodedPublicKey extends Opaque[String]
type EncodedPublicKey = EncodedPublicKey.OpaqueType

object EncodedPrivateKey extends Opaque[String]
type EncodedPrivateKey = EncodedPrivateKey.OpaqueType

case class EncodedKeyPair(privateKey: EncodedPrivateKey, publicKey: EncodedPublicKey)
