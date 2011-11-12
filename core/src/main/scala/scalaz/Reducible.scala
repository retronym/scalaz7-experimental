package scalaz

trait Reducible[F[_]] extends Foldable[F] { self =>
  ////
  def reduceMap[A, B](fa: F[A])(f: A => B)(implicit B: Semigroup[B]): B

  def reduceRight[A](fa: F[A])(f: (A, => A) => A): A

  def reduceLeft[A](fa: F[A])(f: (A, A) => A): A

  // derived functions
  def foldMap[A, B](fa: F[A])(f: A => B)(implicit F: Monoid[B]): B = reduceMap(fa)(f)

  ////
  val reducibleSyntax = new scalaz.syntax.ReducibleSyntax[F] {}
}

object Reducible {
  @inline def apply[F[_]](implicit F: Reducible[F]): Reducible[F] = F

  ////

  ////
}

