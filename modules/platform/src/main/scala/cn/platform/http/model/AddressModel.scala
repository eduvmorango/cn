package cn.platform.http.model

import cn.platform.http.HttpException
import cn.platform.http.model.r.address.CreatedAddressResponse
import sttp.tapir.*
import sttp.tapir.json.circe.*

object AddressModel:

  val postCreateAddress = endpoint
    .post
    .in("address")
    .out(jsonBody[CreatedAddressResponse])
    .errorOut(HttpException.errorOut)
