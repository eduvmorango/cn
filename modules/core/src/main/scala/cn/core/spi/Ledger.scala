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
    nonce: Map[Address, Nonce],
    utxo: Map[TransactionId, (TransactionOutput, Int)],
    accNewOutputs: List[TransactionOutput]
  ):
    self =>

    def filterUtxo(ts: List[TransactionId]): TransactionData =
      TransactionData(self.addresses, self.nonce, utxo.view.filterKeys(!ts.contains(_)).toMap, accNewOutputs)

  object TransactionData:

    def empty(addresses: List[Address]): TransactionData =
      TransactionData(addresses, Map.empty, Map.empty, List.empty)

    def empty(address: Address): TransactionData = TransactionData(List(address), Map.empty, Map.empty, List.empty)
