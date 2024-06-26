package cn.core.shared

import cats.Functor
import cats.Show
import cats.effect.std.UUIDGen
import cats.implicits.*
import cats.kernel.Eq
import cats.kernel.Hash
import java.util.UUID
import monocle.Iso

/** Based on https://github.com/programaker/Octogato/blob/main/src/main/scala/octogato/common/Opaque.scala
  *
  * Generates an `opaque type` with `apply` and `value` to wrap/unwrap a value in it, in addition to some basic given
  * instances.
  *
  * Usage example:
  * {{{
  * object Name extends Opaque[String]
  * type Name = Name.OpaqueType
  * }}}
  */
transparent trait Opaque[T]:

  final opaque type OpaqueType = T

  inline def apply(t: T): OpaqueType             = t
  def unapply(ot: OpaqueType): Some[T]           = Some(ot)
  inline def wrap[F[_]](ts: F[T]): F[OpaqueType] = ts

  def rnd[F[_] : UUIDGen : Functor](using
    ev: UUID =:= T
  ): F[OpaqueType] =
    UUIDGen[F].randomUUID.map(uuid => apply(ev.apply(uuid)))

  extension (ot: OpaqueType) inline def value: T              = ot
  extension [F[_]](ot: F[OpaqueType]) inline def unwrap: F[T] = ot

  given (using
    CanEqual[T, T]
  ): CanEqual[OpaqueType, OpaqueType] = summon

  given (using
    Show[T]
  ): Show[OpaqueType] = summon

  given (using
    Eq[T]
  ): Eq[OpaqueType] = summon

  given (using
    Hash[T]
  ): Hash[OpaqueType] = summon

  given Iso[OpaqueType, T] = Iso[OpaqueType, T](_.value)(apply(_))
