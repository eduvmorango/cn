package cn.platform.service

import cats.effect.IO
import cats.effect.kernel.Resource
import java.security.KeyPairGenerator
import fs2.Stream
import cn.core.api.Transaction
import cn.core.api.Address
import cn.core.shared.types.Amount
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.ECNamedCurveTable
import java.security.SecureRandom
import cn.core.api.AddressKeyPair


trait AddressService[F[_]]:

  def createAddress: F[AddressKeyPair]

  def transactions(address: Address): Stream[F, Transaction]

  def checkAmount(address: Address): Stream[F, Amount]

  def transferAmount(sender: Address, destination: Address, amount: Amount): F[Either[Throwable, Transaction]]





object AddressService:

  val alg = "secp256k1"

  def apply(signService: SignerService[IO]): Resource[IO, AddressService[IO]] =

    Resource.eval(
      IO.delay:

          Security.addProvider(BouncyCastleProvider())

          val ec = ECNamedCurveTable.getParameterSpec("secp256k1")

          val generator = KeyPairGenerator.getInstance("EC", "BC")

          generator.initialize(ec, SecureRandom())
          generator

        ).map(kg =>
          new AddressService[IO]:
              def createAddress: IO[AddressKeyPair] =
                IO.delay(signService.generateAddressKeyPair)

              def checkAmount(address: Address): Stream[IO, Amount] = ???

              def transactions(address: Address): Stream[IO, Transaction] = ???

              def transferAmount(sender: Address, destination: Address, amount: Amount): IO[Either[Throwable, Transaction]] = ???



    )
