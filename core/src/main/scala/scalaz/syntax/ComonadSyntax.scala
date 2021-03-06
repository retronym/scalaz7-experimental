package scalaz
package syntax

/** Wraps a value `self` and provides methods related to `CoMonad` */
trait CoMonadV[F[_],A] extends SyntaxV[F[A]] {
  implicit def F: CoMonad[F]
  ////

  ////
}

trait ToCoMonadV extends ToCoPointedV with ToCoJoinV with ToCoBindV {
  implicit def ToCoMonadV[FA](v: FA)(implicit F0: Unapply[CoMonad, FA]) =
    new CoMonadV[F0.M,F0.A] { def self = F0(v); implicit def F: CoMonad[F0.M] = F0.TC }

  ////

  ////
}

trait CoMonadSyntax[F[_]] extends CoPointedSyntax[F] with CoJoinSyntax[F] with CoBindSyntax[F] {
  implicit def ToCoMonadV[A](v: F[A])(implicit F0: CoMonad[F]): CoMonadV[F, A] = new CoMonadV[F,A] { def self = v; implicit def F: CoMonad[F] = F0 }

  ////

  ////
}
