package cn.core.shared

import constraints.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

private object constraints:

  type ValidAmount = Greater[0] DescribedAs "Invalid Amount"

object types:

  object CnHash extends Opaque[String]
  type CnHash = CnHash.OpaqueType

  opaque type Amount = Double :| ValidAmount
  object Amount extends RefinedTypeOps[Double, ValidAmount, Amount]
