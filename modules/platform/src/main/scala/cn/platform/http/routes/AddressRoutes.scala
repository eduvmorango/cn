package cn.platform.http.routes

import cats.effect.IO
import cats.syntax.all.*
import cn.platform.http.model.AddressModel.*
import cn.platform.http.model.r.address.*
import cn.platform.service.AddressService
import sttp.tapir.server.http4s.Http4sServerInterpreter

class AddressRoutes(addressService: AddressService[IO]):

  val postCreateAddressEndpoint =
    postCreateAddress.serverLogic(
      req => addressService.createAddress.map(a => CreatedAddressResponse.fromDomain(a).asRight)
    )

  val endpoints = List(postCreateAddressEndpoint)

  val routes = Http4sServerInterpreter[IO]().toRoutes(endpoints)
