package cn.core.spi

import cn.core.api.Address
import cn.core.api.Block
import cn.core.api.LedgerHeader
import cn.core.api.Transaction
import cn.core.api.TransactionId
import cn.core.api.TransactionOutput
import fs2.Stream

trait Ledger[F[_]]:

  def header: LedgerHeader

  def current: F[Block]

  def blocks: Stream[F, Block]

  def appendTransaction(transaction: Transaction): F[Unit]

  def getAvailableUtxosForAddress(address: Address): F[Map[TransactionId, (TransactionOutput, Int)]]
