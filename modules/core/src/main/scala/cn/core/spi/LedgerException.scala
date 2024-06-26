package cn.core.spi

import cn.core.api.CnException

sealed trait LedgerException extends CnException

object LedgerException:

  object AddressNotFound extends LedgerException:

    def message: String = "The ledger doesn't contains transactions for this address"
