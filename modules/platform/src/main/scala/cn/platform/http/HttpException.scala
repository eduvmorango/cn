package cn.platform.http

import cn.core.api.CnException
import io.circe.Codec
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.Schema
import sttp.tapir.json.circe.*

sealed trait HttpException extends CnException

object HttpException:

  case object NotFound extends HttpException:

    override def message: String = "Not Found"

  case class BadRequest(message: String) extends HttpException

  case class UnprocessableEntity(message: String)                    extends HttpException
  case class InternalServerError(message: String = "Internal Error") extends HttpException

  given Codec[UnprocessableEntity] = Codec.AsObject.derived
  given Codec[InternalServerError] = Codec.AsObject.derived

  given Schema[UnprocessableEntity] = Schema.derived
  given Schema[InternalServerError] = Schema.derived

  val errorOut = oneOf[HttpException](
    oneOfVariantFromMatchType(StatusCode.InternalServerError, jsonBody[InternalServerError]),
    oneOfVariantFromMatchType(StatusCode.UnprocessableEntity, jsonBody[UnprocessableEntity]),
    oneOfVariantFromMatchType(StatusCode.NotFound, emptyOutputAs(NotFound))
  )
