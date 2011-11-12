package scalaz
package syntax

/** Wraps a value `self` and provides methods related to `Reducible` */
trait ReducibleV[F[_],A] extends SyntaxV[F[A]] {
  implicit def F: Reducible[F]
  ////

  ////
}

trait ToReducibleV extends ToFoldableV {
  implicit def ToReducibleV[FA](v: FA)(implicit F0: Unapply[Reducible, FA]) =
    new ReducibleV[F0.M,F0.A] { def self = F0(v); implicit def F: Reducible[F0.M] = F0.TC }

  ////

  ////
}

trait ReducibleSyntax[F[_]] extends FoldableSyntax[F] {
  implicit def ToReducibleV[A](v: F[A])(implicit F0: Reducible[F]): ReducibleV[F, A] = new ReducibleV[F,A] { def self = v; implicit def F: Reducible[F] = F0 }

  ////

  ////
}
