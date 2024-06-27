package cn.core.spi

import cn.core.api.CnException

sealed trait LedgerException extends CnException

object LedgerException:

  object MissingPrivateKey extends LedgerException:

    def message: String = "The ledger headers doesn't contains the private key"

  object BlockDereferenced extends LedgerException:

    def message: String = "The block lost the reference"

  object AddressNotFound extends LedgerException:

    def message: String = "The ledger doesn't contains transactions for this address"
