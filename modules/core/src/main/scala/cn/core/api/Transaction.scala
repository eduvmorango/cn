package cn.core.api

import cats.kernel.Eq
import cats.kernel.Hash
import cats.syntax.all.*
import cn.core.api.TransactionException.InsufficientAmount
import cn.core.shared.Opaque
import cn.core.shared.types.*
import io.github.arainko.ducktape.*
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

case class TransactionOutput(address: Address, amount: Amount)

object TransactionOutput:

  given Eq[TransactionOutput]   = Eq.fromUniversalEquals
  given Hash[TransactionOutput] = Hash.fromUniversalHashCode

case class UnsignedTransaction(
  nonce: Nonce,
  is: List[TransactionInput],
  os: List[TransactionOutput]
):
  self =>

  def toTransaction(sig: Signature) = self.into[Transaction].transform(Field.const(_.signature, sig))

  def hash: CnHash = CnHash(Hash.hash(self).toHexString)

object UnsignedTransaction:

  given Eq[UnsignedTransaction]   = Eq.fromUniversalEquals
  given Hash[UnsignedTransaction] = Hash.fromUniversalHashCode

case class Transaction(signature: Signature, nonce: Nonce, is: List[TransactionInput], os: List[TransactionOutput]):
  self =>

  def hash: CnHash = CnHash(Hash.hash(self).toHexString)

case class SignedTransaction(sig: Signature, transaction: Transaction):
  def hash: CnHash = transaction.hash

object Transaction:

  given Eq[Transaction]   = Eq.fromUniversalEquals
  given Hash[Transaction] = Hash.fromUniversalHashCode

case class TransactionRequest(
  source: Address,
  outputs: Set[TransactionOutput],
  timestamp: OffsetDateTime
):
  self =>

  val hash: CnHash = CnHash(Hash.hash(self).toHexString)

  val totalAmount = self.outputs.toList.map(_._2.value.toDouble).sum

  def calculateTransaction(
    nonce: Nonce,
    utxos: Map[TransactionId, (TransactionOutput, Int)]
  ): Either[CnException, UnsignedTransaction] =
    val (inputs, newOutputs, remaining) =
      utxos.foldLeft[(List[TransactionInput], List[TransactionOutput], Double)](
        (List.empty[TransactionInput], List.empty[TransactionOutput], self.totalAmount)
      )(
        (acc, cur) =>
          val (inps, outs, remainingAmount) = acc
          val (txId, (currentOutput, idx))  = cur

          val currentValue: Double = currentOutput.amount.value

          val newOutputValue = outs.map(_.amount.value.toDouble).sum

          val diff = Math.abs(currentValue - remainingAmount)

          println(outputs.mkString("\n"))
          println(diff)

          if remainingAmount == 0 then acc
          else if currentValue == remainingAmount then
            (
              inps ++ List(TransactionInput(txId, idx, Amount.applyUnsafe(currentValue), self.source)),
              outs ++ self.outputs,
              0
            )
          else if currentValue > remainingAmount then
            (
              inps ++ List(TransactionInput(txId, idx, Amount.applyUnsafe(currentValue), self.source)),
              outs ++ List(TransactionOutput(self.source, Amount.applyUnsafe(diff))) ++ self.outputs,
              0
            )
          else (List(TransactionInput(txId, idx, Amount.applyUnsafe(currentValue), self.source)), outs, diff)
      )

    if remaining != 0 then Left(InsufficientAmount)
    else Right(UnsignedTransaction(nonce, inputs, newOutputs))

object TransactionRequest:

  given Eq[TransactionRequest]   = Eq.by(_.hash)
  given Hash[TransactionRequest] = Hash.by(t => (t.source, t.outputs.hashCode.toHexString, t.timestamp.toString))
