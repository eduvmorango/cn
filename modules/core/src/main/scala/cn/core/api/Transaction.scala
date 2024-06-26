package cn.core.api

import cats.kernel.Eq
import cats.kernel.Hash
import cats.syntax.all.*
import cn.core.shared.Opaque
import cn.core.shared.types.*
import java.time.OffsetDateTime

object TransactionId extends Opaque[String]
type TransactionId = TransactionId.OpaqueType

object TransactionHash extends Opaque[String]
type TransactionHash = TransactionHash.OpaqueType

object Nonce extends Opaque[Int]
type Nonce = Nonce.OpaqueType

case class TransactionInput(txId: TransactionId, index: Int, amount: Amount, address: Address)

object TransactionInput:

  given Eq[TransactionInput]   = Eq.fromUniversalEquals
  given Hash[TransactionInput] = Hash.fromUniversalHashCode

case class TransactionOutput(amount: Amount, address: Address)

object TransactionOutput:

  given Eq[TransactionOutput]   = Eq.fromUniversalEquals
  given Hash[TransactionOutput] = Hash.fromUniversalHashCode

case class Transaction(is: List[TransactionInput], os: List[TransactionOutput]):
  self =>

  def hash: CnHash = CnHash(Hash.hash(self).toHexString)

case class SignedTransaction(sig: Signature, transaction: Transaction):
  def hash: CnHash = transaction.hash

object Transaction:

  given Eq[Transaction]   = Eq.fromUniversalEquals
  given Hash[Transaction] = Hash.fromUniversalHashCode

case class TransactionRequest(
  source: Address,
  destination: Address,
  amount: Amount,
  nonce: Nonce,
  timestamp: OffsetDateTime
):
  self =>

  val hash: CnHash = CnHash(Hash.hash(self).toHexString)

  def calculateTransaction(
    utxos: Map[TransactionId, (TransactionOutput, Int)]
  ): Either[String, Transaction] =
    val (inputs, newOutputs, remaining) =
      utxos.foldLeft[(List[TransactionInput], List[TransactionOutput], Double)](
        (List.empty[TransactionInput], List.empty[TransactionOutput], self.amount.value)
      )(
        (acc, cur) =>
          val (inps, outs, remainingAmount) = acc
          val (txId, curOut)                = cur

          val currentValue: Double = curOut._1.amount.value
          val idx                  = curOut._2

          val diff = currentValue - remainingAmount

          if remainingAmount == 0 then acc
          else if currentValue == remainingAmount then
            (
              inps ++ List(TransactionInput(txId, idx, Amount.applyUnsafe(currentValue), self.source)),
              outs ++ List(TransactionOutput(Amount.applyUnsafe(remainingAmount), self.destination)),
              0
            )
          else if currentValue > remainingAmount then
            (
              inps ++ List(TransactionInput(txId, idx, Amount.applyUnsafe(currentValue), self.source)),
              outs ++
                List(
                  TransactionOutput(Amount.applyUnsafe(remainingAmount), self.destination),
                  TransactionOutput(Amount.applyUnsafe(diff), self.source)
                ),
              0
            )
          else
            (
              List(TransactionInput(txId, idx, Amount.applyUnsafe(currentValue), self.source)),
              outs ++
                List(TransactionOutput(Amount.applyUnsafe(currentValue), self.destination)),
              diff
            )
      )

    if remaining != 0 then Left("Insufficient Amount")
    else Right(Transaction(inputs, newOutputs))

object TransactionRequest:

  given Eq[TransactionRequest]   = Eq.by(_.hash)
  given Hash[TransactionRequest] = Hash.by(t => (t.source, t.destination, t.nonce, t.timestamp.toString))
