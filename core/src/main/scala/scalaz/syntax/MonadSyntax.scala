package scalaz
package syntax

/** Wraps a value `self` and provides methods related to `Monad` */
trait MonadV[F[_],A] extends SyntaxV[F[A]] {
  implicit def F: Monad[F]
  ////

  ////
}

trait ToMonadV extends ToApplicativeV with ToBindV {
  implicit def ToMonadV[FA](v: FA)(implicit F0: Unapply[Monad, FA]) =
    new MonadV[F0.M,F0.A] { def self = F0(v); implicit def F: Monad[F0.M] = F0.TC }

  ////

  ////
}

trait MonadSyntax[F[_]] extends ApplicativeSyntax[F] with BindSyntax[F] {
  implicit def ToMonadV[A](v: F[A])(implicit F0: Monad[F]): MonadV[F, A] = new MonadV[F,A] { def self = v; implicit def F: Monad[F] = F0 }

  ////

  ////
}
