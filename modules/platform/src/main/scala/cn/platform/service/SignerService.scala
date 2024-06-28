package cn.platform.service

import cats.effect.kernel.Resource
import cats.effect.IO
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.ECNamedCurveTable
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.Base64
import cn.core.api.EncodedPrivateKey
import cn.core.api.EncodedPublicKey
import java.security.PublicKey
import java.security.PrivateKey
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import cn.core.api.Signature
import cn.core.shared.types.CnHash
import java.security.spec.X509EncodedKeySpec
import cn.core.api.AddressKeyPair
import cn.core.api.Address
import java.security.MessageDigest
import cats.syntax.all.*
import cn.core.api.SignedTransaction
import cn.core.api.CnException
import cn.platform.service.SignerException.SigningError
import cn.core.api.Transaction

sealed trait SignerException extends CnException

object SignerException:

  case object SigningError extends SignerException:
    def message = "Signature error"



trait SignerService[F[_]]:

  def generateAddressKeyPair: AddressKeyPair

  def decodePublicKey(key: EncodedPublicKey): F[PublicKey]

  def decodePrivateKey(key: EncodedPrivateKey): F[PrivateKey]

  def sign(hash: CnHash, privateKey: EncodedPrivateKey): F[Signature]

  def checkSignature(transaction: Transaction, publicKey: EncodedPublicKey): F[Boolean]

  def checkPublicKeyAndAddress(address: Address, publicKey: EncodedPublicKey ): F[Boolean]



object SignerService:

  val alg = "secp256k1"

  def default: Resource[IO, SignerService[IO]] =
    Resource.eval(
      IO.delay:
        Security.addProvider(BouncyCastleProvider())

        val ec = ECNamedCurveTable.getParameterSpec(alg)

        val generator = KeyPairGenerator.getInstance("EC", "BC")

        generator.initialize(ec, SecureRandom())

        val keyFactory = KeyFactory.getInstance("EC", "BC")

        (generator, keyFactory)
      .map((gen, keyFactory) =>
        new SignerService[IO]:


          def deriveAddress(publicKey: Array[Byte]): Address =
            def base58(input: Array[Byte]): String =
              val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

              def loop(num: BigInt, builder: StringBuilder): StringBuilder =
                if num > 0 then
                  val (quo, rem) = num /% 58
                  builder.insert(0, alphabet(rem.toInt))
                  loop(quo, builder)
                else
                  builder

              val bi = BigInt(1, input)
              val builder = loop(bi, StringBuilder())

              val count = input.count(_ == 0)

              builder.insert(0, List.fill(count)('1').mkString).result

            val sha256 = MessageDigest.getInstance("SHA-256")
            val ripemd160 = MessageDigest.getInstance("RIPEMD160", "BC")
            val sha256Hash = sha256.digest(publicKey)
            val ripemd160Hash = ripemd160.digest(sha256Hash)
            val versionedPayload = Array(0x00.toByte) ++ ripemd160Hash
            val checksum = sha256.digest(sha256.digest(versionedPayload)).take(4)
            val binaryAddress = versionedPayload ++ checksum
            Address(base58(binaryAddress))


          def generateAddressKeyPair: AddressKeyPair=
             val kp = gen.genKeyPair()
             val enc = Base64.getEncoder()
             val address = deriveAddress(kp.getPublic().getEncoded())


             AddressKeyPair(
               address,
              EncodedPublicKey(enc.encodeToString(kp.getPublic().getEncoded())),
              Some(EncodedPrivateKey(enc.encodeToString(kp.getPrivate().getEncoded())))
             )

          def decodePublicKey(key: EncodedPublicKey): IO[PublicKey] =
            IO.delay:
              val publicKey = X509EncodedKeySpec(Base64.getDecoder().decode(key.value))
              keyFactory.generatePublic(publicKey)
            .adaptError(_ => SigningError)


          def decodePrivateKey(key: EncodedPrivateKey): IO[PrivateKey] =
            IO.delay(keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(key.value))))
              .recoverWith(_ => IO.raiseError(SigningError))


          def sign(hash: CnHash, privateKey: EncodedPrivateKey): IO[Signature] =
            decodePrivateKey(privateKey).flatMap(pk =>
                IO.delay:
                  val sig = java.security.Signature.getInstance("SHA256withECDSA", "BC")
                  sig.initSign(pk)
                  sig.update(hash.value.getBytes("UTF-8"))
                  val signedBytes = sig.sign()
                  Signature(Base64.getEncoder().encodeToString(signedBytes))

            ).recoverWith(_ => IO.raiseError(SigningError))


          def checkSignature(transaction: Transaction, publicKey: EncodedPublicKey): IO[Boolean] =
              decodePublicKey(publicKey).flatMap(pk =>
                IO.delay:
                  val sig = java.security.Signature.getInstance("SHA256withECDSA", "BC")
                  sig.initVerify(pk)
                  sig.update(transaction.hash.value.getBytes("UTF-8"))
                  sig.verify(Base64.getDecoder().decode(transaction.signature.value))
              ).adaptError(_ => SigningError)


          def checkPublicKeyAndAddress(address: Address, publicKey: EncodedPublicKey): IO[Boolean] =
            IO.delay:
              val bytes = Base64.getDecoder().decode(publicKey.value)
              deriveAddress(bytes) === address
            .adaptError(_ => SigningError)


      )
    )
