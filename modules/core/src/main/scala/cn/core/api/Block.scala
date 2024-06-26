package cn.core.api

import cats.data.NonEmptyList
import cats.kernel.Eq
import cats.kernel.Hash
import cn.core.shared.types.CnHash
import java.time.OffsetDateTime
import scala.annotation.tailrec

case class Block(
  prior: Option[Block],
  transactions: NonEmptyList[Transaction],
  signature: Option[Signature],
  nonce: Nonce,
  timestamp: OffsetDateTime,
  index: Int
):
  self =>

  val hash: CnHash = CnHash(Hash.hash(self).toHexString)

  val flatten: List[Block] =

    @tailrec
    def flatten0(block: Block, cur: List[Block]): List[Block] =
      block.prior match
      case None        => cur
      case Some(value) => flatten0(value, block :: cur)

    self.prior match
    case None => List(self)
    case _    => flatten0(self, Nil)

  def sign(signature: Signature): Block = self.copy(signature = Some(signature))

object Block:

  def apply(
    prior: Block,
    transactions: NonEmptyList[Transaction],
    nonce: Nonce,
    timestamp: OffsetDateTime
  ) = new Block(Some(prior), transactions, None, nonce, timestamp, prior.index + 1)

  given [A : Hash]: Hash[NonEmptyList[A]] = Hash.by(_.toList)
  given Eq[Block]                         = Eq.by(_.hash)

  given Hash[Block] =
    Hash.by(
      b =>
        (
          b.prior.map(_.hash).getOrElse(CnHash("")),
          b.transactions.toList.hashCode.toHexString,
          b.nonce,
          b.timestamp.toString
        )
    )
