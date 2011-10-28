package scalaz
package syntax

/** Wraps a value `self` and provides methods related to `Category` */
trait CategoryV[F[_, _],A, B] extends SyntaxV[F[A, B]] {
  implicit def F: Category[F]
  ////

  ////
}

trait ToCategoryV extends ToArrIdV with ToComposeV {
  implicit def ToCategoryV[F[_, _],A, B](v: F[A, B])(implicit F0: Category[F]) =
    new CategoryV[F,A, B] { def self = v; implicit def F: Category[F] = F0 }

  ////
  implicit def ToCategoryVFromKliesliLike[G[_], F[G[_], _, _],A, B](v: F[G, A, B])(implicit F0: Category[({type λ[α, β]=F[G, α, β]})#λ]) =
      new CategoryV[({type λ[α, β]=F[G, α, β]})#λ, A, B] { def self = v; implicit def F: Category[({type λ[α, β]=F[G, α, β]})#λ] = F0 }

  ////
}

trait CategorySyntax[F[_, _]] extends ArrIdSyntax[F] with ComposeSyntax[F] {
  implicit def ToCategoryV[A, B](v: F[A, B])(implicit F0: Category[F]): CategoryV[F, A, B] = new CategoryV[F, A, B] { def self = v; implicit def F: Category[F] = F0 }

  ////

  ////
}
