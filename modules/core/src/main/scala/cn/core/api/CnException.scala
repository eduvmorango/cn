package cn.core.api

import scala.util.control.NoStackTrace

trait CnException extends Throwable with NoStackTrace:
  def message: String

sealed trait TransactionException extends CnException

object TransactionException:

  case object InsufficientAmount extends CnException:
    def message = "Insufficient Funds to execute transaction"

  case object InvalidSignature extends CnException:
    def message = "Invalid Signature"

  case object InvalidAddressPublicKey extends CnException:
    def message = "The address isn't bounded to the Public Key"
