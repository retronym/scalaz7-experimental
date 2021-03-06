package scalaz.example

/**Examples showing the use of Unapply to reduce the need for type annotations */
object UnapplyInference extends App {
  eitherTBifunctor()

  def eitherTBifunctor() {
    import scalaz._, Scalaz._

    val either: Either[Int, Int] = Right(1)
    val eitherT = EitherT(some(either))

    println((eitherT :-> (_ - 1)).runT) // Some(Right(0))
  }

  def eitherTBitraverse() {
    import scalaz._
    import std.list._, std.option._
    import syntax.all._

    val either: Either[List[Int], List[Int]] = Right(List(1))
    val eitherT: EitherT[Option, List[Int], List[Int]] = EitherT(some(either))

    val bisequence: List[EitherT[Option, Int, Int]] = eitherT.bisequence[List, Int, Int]
  }


  // Without Unapply
  def stateTraverse1 {
    import scalaz._, Scalaz._
    import State.{State, stateMonad}
    val ls = List(1, 2, 3)
    val traverseOpt: Option[List[Int]] = ls.traverse(a => some(a))
    val traverseState: State[Int, List[Int]] = ls.traverse[({type λ[α] = State[Int, α]})#λ, Int](a => State((x: Int) => (x + 1, a)))
  }

  // With Unapply (in the signature of traverseI)
  def stateTraverse2 {
    import scalaz._, Scalaz._
    val ls = List(1, 2, 3)
    val traverseOpt: Option[List[Int]] = ls.traverseU(a => some(a))
    val traverseState = ls.traverseU(a => State((x: Int) => (x + 1, a)))

    val pair: State.State[Int, (Int, Int)] = State((x: Int) => (x + 1, x)).pair(State((x: Int) => (x + 2, x)))
  }

  def kliesliCompose() {
    import scalaz._
    import std.option._
    import syntax.compose._
    import Kleisli._

    val k = kleisli((a: Int) => some(a + 1))
    k >>> k
  }
}