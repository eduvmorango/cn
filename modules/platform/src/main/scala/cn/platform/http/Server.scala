package cn.platform.http

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.ResourceApp
import cats.effect.kernel.Resource
import cn.platform.http.routes.AddressRoutes
import cn.platform.service.AddressService
import cn.platform.service.SignerService
import cats.syntax.all._
import org.http4s.server.Router
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.*
import cn.platform.service.LedgerService
import cn.platform.iml.SingleNodeLedger
import cn.platform.http.routes.LedgerRoutes

object Server extends ResourceApp:

  def mkRoutes(addressService: AddressService[IO], ledgerService: LedgerService[IO]) =
    List(AddressRoutes(addressService).routes,  LedgerRoutes(ledgerService).routes )

  override def run(args: List[String]): Resource[IO, ExitCode] =
    for
      signerService    <- SignerService.default
      addressService <- AddressService(signerService)
      ledger         <- SingleNodeLedger(signerService)
      ledgerService  <- LedgerService.default(ledger, signerService)
      _ <- ledger.process.compile.drain.background
      _ <- Resource.pure(mkRoutes(addressService, ledgerService)).evalMap:
        routes =>

        val reducedRoutes = routes.reduceLeft(_ <+> _)

        val httpApp = Router.of[IO]("api/v1" -> reducedRoutes).orNotFound


         EmberServerBuilder
           .default[IO]
           .withHost(ipv4"0.0.0.0")
           .withPort(port"8080")
           .withHttpApp(httpApp)
           .build
           .useForever

    yield ExitCode.Success
