package cn.core.spi

import cn.core.api.Address
import cn.core.api.Block
import cn.core.api.LedgerHeader
import cn.core.api.Nonce
import cn.core.api.Transaction
import cn.core.api.TransactionId
import cn.core.api.TransactionOutput
import cn.core.spi.Ledger.TransactionData
import fs2.Stream

trait Ledger[F[_]]:

  def header: LedgerHeader

  def current: F[Block]

  def blocks: Stream[F, Block]

//  def blocksUntil(idx: Int): Stream[F, Block]

  def appendTransaction(transaction: Transaction): F[Unit]

  def getTransactionData(bs: Stream[F, Block], current: TransactionData): F[TransactionData]

object Ledger:

  case class TransactionData(
    addresses: List[Address],
    nonce: Nonce,
    utxo: Map[TransactionId, (TransactionOutput, Int)],
    accNewOutputs: List[TransactionOutput]
  )

  object TransactionData:

    def empty(addresses: List[Address]): TransactionData = TransactionData(addresses, Nonce(0), Map.empty, List.empty)
    def empty(address: Address): TransactionData = TransactionData(List(address), Nonce(0), Map.empty, List.empty)
