package scalaz

/**
 * Represents a type `MA` that has been destructured into as a type constructor `M[_]`
 * applied to type `A`, along with a corresponding type class instance `TC[M]`.
 *
 * The implicit conversions in the companion object provide a means to obtain type class
 * instances for partially applied type constructors, in lieu of direct compiler support
 * as described in [[https://issues.scala-lang.org/browse/SI-2712 SI-2712]].
 *
 * {{{
 * // Directly depending on Applicative[G]
 * def traverse[G[_], B](f: A => G[B])(implicit G: Applicative[G]): G[F[B]] =
 *   G.traverse(self)(f)
 *
 * // Indirect lookup of the Applicative instance
 * // Requires -Ydep-method-types or Scala trunk circa Oct 2011
 * def traverseI[GB](f: A => GB)(implicit G: Instance[Applicative, GB]): G.M[F[G.A]] /*G[F[B]*/ = {
 *   G.TC.traverse(self)(a => G(f(a)))
 * }
 *
 * // Old usage
 * def stateTraverse1 {
 *   import scalaz._, Scalaz._
 *   import State.{State, stateMonad}
 *   val ls = List(1, 2, 3)
 *   val traverseOpt: Option[List[Int]] = ls.traverse(a => Some(a))
 *   val traverseState: State[Int, List[Int]] = ls.traverse[({type λ[α]=State[Int, α]})#λ, Int](a => State((x: Int) => (x + 1, a)))
 * }
 *
 * // New usage
 * def stateTraverse2 {
 *   import scalaz._, Scalaz._
 *   val ls = List(1, 2, 3)
 *   val traverseOpt: Option[List[Int]] = ls.traverseI(a => some(a))
 *   val traverseState = ls.traverseI(a => State((x: Int) => (x + 1, a)))
 * }
 *
 * }}}
 *
 * Credits to Miles Sabin.
 */
trait Unapply[TC[_[_]], MA] {

  /** The type constructor */
  type M[_]

  /** The type that `M` was applied to */
  type A

  /** The instance of the type class */
  def TC: TC[M]

  /** Evidence that MA =:= M[A] */
  def apply(ma: MA): M[A]
}

trait Unapply0 {
  // Things get tricky with type State[S, A] = StateT[Id, S, A], both unapplyMAB2 and unapplyMFAB2 are applicable
  // Without characterizing this fully, I'm using the standard implicit prioritization to avoid this.

  /**Unpack a value of type `M0[F[_], A0, B0]` into types `[a]M0[F, a, B0]` and `A0`, given an instance of `TC` */
  implicit def unapplyMFAB1[TC[_[_]], F[_], M0[F[_], _, _], A0, B0](implicit TC0: TC[({type λ[α] = M0[F, α, B0]})#λ]) = new Unapply[TC, M0[F, A0, B0]] {
    type M[X] = M0[F, X, B0]
    type A = A0
    def TC = TC0
    def apply(ma: M0[F, A0, B0]) = ma
  }

  /**Unpack a value of type `M0[F[_], A0, B0]` into types `[b]M0[F, A0, b]` and `B0`, given an instance of `TC` */
  implicit def unapplyMFAB2[TC[_[_]], F[_], M0[F[_], _, _], A0, B0](implicit TC0: TC[({type λ[β] = M0[F, A0, β]})#λ]) = new Unapply[TC, M0[F, A0, B0]] {
    type M[X] = M0[F, A0, X]
    type A = B0
    def TC = TC0
    def apply(ma: M0[F, A0, B0]) = ma
  }
}

object Unapply extends Unapply0 {

  /** Unpack a value of type `M0[A0]` into types `M0` and `A9`, given a instance of `TC` */
  implicit def unapplyMA[TC[_[_]], M0[_], A0](implicit TC0: TC[M0]) = new Unapply[TC, M0[A0]] {
    type M[X] = M0[X]
    type A = A0
    def TC = TC0
    def apply(ma: M0[A0]) = ma
  }


  /** Unpack a value of type `M0[A0, B0]` into types `[a]M0[A, B0]` and `A`, given an instance of `TC` */
  implicit def unapplyMAB1[TC[_[_]], M0[_, _], A0, B0](implicit TC0: TC[({type λ[α]=M0[α, B0]})#λ]) = new Unapply[TC, M0[A0, B0]] {
    type M[X] = M0[X, B0]
    type A = A0
    def TC = TC0
    def apply(ma: M0[A0, B0]) = ma
  }

  /** Unpack a value of type `M0[A0, B0]` into types `[b]M0[A0, b]` and `B`, given an instance of `TC` */
  implicit def unapplyMAB2[TC[_[_]], M0[_, _], A0, B0](implicit TC0: TC[({type λ[α]=M0[A0, α]})#λ]) = new Unapply[TC, M0[A0, B0]] {
    type M[X] = M0[A0, X]
    type A = B0
    def TC = TC0
    def apply(ma: M0[A0, B0]) = ma
  }

  // TODO More!
}

trait Unapply2[TC[_[_, _]], MAB] {

  /** The type constructor */
  type M[_, _]

  /** The first type that `M` was applied to */
  type A

  /** The second type that `M` was applied to */
  type B

  /** The instance of the type class */
  def TC: TC[M]

  /** Evidence that MA =:= M[A] */
  def apply(ma: MAB): M[A, B]
}

trait Unapply20 {
  /**Unpack a value of type `M0[F[_], A0, B0]` into types `[a, b]=M0[F, a, b]`, `A0`, and 'B9', given an instance of `TC` */
  implicit def unapplyMFAB[TC[_[_, _]], F[_], M0[F[_], _, _], A0, B0](implicit TC0: TC[({type λ[α, β] = M0[F, α, β]})#λ]) = new Unapply2[TC, M0[F, A0, B0]] {
    type M[X, Y] = M0[F, X, Y]
    type A = A0
    type B = B0
    def TC = TC0
    def apply(ma: M0[F, A0, B0]) = ma
  }
}

object Unapply2 extends Unapply20 {
  /**Unpack a value of type `M0[A0, B0]` into types `M0`, `A`, and 'B', given an instance of `TC` */
  implicit def unapplyMAB[TC[_[_, _]], M0[_, _], A0, B0](implicit TC0: TC[M0]) = new Unapply2[TC, M0[A0, B0]] {
    type M[X, Y] = M0[X, Y]
    type A = A0
    type B = B0
    def TC = TC0
    def apply(ma: M0[A0, B0]) = ma
  }
}

trait UnapplyProduct[TC[_[_]], MA, MB] {
  type M[X]
  type A
  type B
  def TC: TC[M]
  type MA_ = MA
  def _1(ma: MA): M[A]
  def _2(mb: MB): M[B]
}

object UnapplyProduct {
  import Isomorphism.<~>
  // This seems to motivate multiple implicit parameter sections. Is there another way?
  // Currently, a type annotation in a parameter declaration may be path-dependent on a
  // parameter from a previous parameter section, hence `iso` can't be in the first parameter
  // section; which itself can't be implicit.
  //
  // There are two possible changes to Scalac that could help:
  //
  // 1. Allow multiple implicit parameter sections
  // 2. Allow path-dependent parameter types to refer to the current (or even subsequent)
  //    parameter sections.
  //
  //    A motivating example for #2 is in neg/depmet_try_implicit.scala
  //
  //    def foo[T, T2](a: T, x: T2)(implicit w: ComputeT2[T, T2]) // awkward, if you provide T you must also provide T2
  //    def foo[T](a: T, x: w.T2)(implicit w: ComputeT2[T])       // more compact, and allows you to provide T1 and infer T2.
  //
  /*implicit */ def unapply[TC[_[_]], MA0, MB0](/*implicit */U1: Unapply[TC, MA0], U2: Unapply[TC, MB0])(implicit iso: U1.M <~> U2.M) = new UnapplyProduct[TC, MA0, MB0] {
    type M[X] = U1.M[X]
    type A = U1.A
    type B = U2.A
    def TC = U1.TC
    def _1(ma: MA0) = U1(ma)
    def _2(mb: MB0) = iso.from(U2(mb))
  }
}
