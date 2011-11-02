package scalaz
package iteratee

import effect._

sealed trait IterateeT[X, E, F[_], A] {
  def value: F[StepT[X, E, F, A]]

  def foldT[Z](
                cont: (Input[E] => IterateeT[X, E, F, A]) => F[Z]
                , done: (=> A, => Input[E]) => F[Z]
                , err: (=> X) => F[Z]
                )(implicit F: Bind[F]): F[Z] =
    F.bind(value)((s: StepT[X, E, F, A]) => s(cont, done, err))

  def apply(e: (=> X) => F[A])(implicit F: Monad[F]): F[A] = {
    val lifte: (=> X) => IterateeT[X, E, F, A] = x => MonadTrans[({type λ[α[_], β] = IterateeT[X, E, α, β]})#λ].liftM(e(x))
    F.bind(>>==(enumEofT(lifte)).value)((s: StepT[X, E, F, A]) => s.fold(
      cont = _ => sys.error("diverging iteratee")
      , done = (a, _) => F.pure(a)
      , err = e
    ))
  }

  def flatMap[B](f: A => IterateeT[X, E, F, B])(implicit F: Monad[F]): IterateeT[X, E, F, B] = {
    def through(x: IterateeT[X, E, F, A]): IterateeT[X, E, F, B] =
      iterateeT(
        F.bind(x.value)((s: StepT[X, E, F, A]) => s.fold[F[StepT[X, E, F, B]]](
          cont = k => F.pure(StepT.scont(u => through(k(u))))
          , done = (a, i) =>
            if (i.isEmpty)
              f(a).value
            else
              F.bind(f(a).value)(_.fold(
                cont = kk => kk(i).value
                , done = (aa, _) => F.pure(StepT.sdone[X, E, F, B](aa, i))
                , err = ee => F.pure(StepT.serr[X, E, F, B](ee))
              ))
          , err = e => F.pure(StepT.serr(e))
        )))
    through(this)
  }

  def map[B](f: A => B)(implicit m: Monad[F]): IterateeT[X, E, F, B] = {
    flatMap(a => StepT.sdone[X, E, F, B](f(a), emptyInput).pointI)
  }

  def >>==[B, C](f: StepT[X, E, F, A] => IterateeT[X, B, F, C])(implicit m: Bind[F]): IterateeT[X, B, F, C] =
    iterateeT(m.bind(value)((s: StepT[X, E, F, A]) => f(s).value))

  def mapI[G[_]](f: F ~> G)(implicit F: Functor[F]): IterateeT[X, E, G, A] = {
    def step: StepT[X, E, F, A] => StepT[X, E, G, A] =
      _.fold(
        cont = k => scont[X, E, G, A](k andThen loop)
        , done = (a, i) => sdone[X, E, G, A](a, i)
        , err = e => serr[X, E, G, A](e)
      )
    def loop: IterateeT[X, E, F, A] => IterateeT[X, E, G, A] = i => iterateeT(f(F.map(i.value)(step)))
    loop(this)
  }

  def up[G[_]](implicit G: Pointed[G], F: Functor[F], FC: Copointed[F]): IterateeT[X, E, G, A] = {
    mapI(new (F ~> G) {
      def apply[A](a: F[A]) = G.pure(FC.copure(a))
    })
  }

  def joinI[I, B](implicit outer: IterateeT[X, E, F, A] =:= IterateeT[X, E, F, StepT[X, I, F, B]], M: Monad[F]): IterateeT[X, E, F, B] = {
    val ITP = IterateeTMonad[X, E, F]
    def check: StepT[X, I, F, B] => IterateeT[X, E, F, B] = _.fold(
      cont = k => k(eofInput) >>== {
        s => s.mapContOr(_ => sys.error("diverging iteratee"), check(s))
      }
      , done = (a, _) => ITP.pure(a)
      , err = e => err(e)
    )

    outer(this) flatMap check
  }

  def %=[O](e: EnumerateeT[X, O, E, F, A])(implicit m: Monad[F]): IterateeT[X, O, F, A] = {
    (this >>== e).joinI[E, A]
  }

  /**
   * Feeds input elements to this iteratee until it is done, feeds the produced value to the 
   * inner iteratee.  Then this iteratee will start over, looping until the inner iteratee is done.
   */
  def sequenceI[B](implicit m: Monad[F]): EnumerateeT[X, E, A, F, B] = {
    def loop: EnumerateeT[X, E, A, F, B] = doneOr(checkEof)
    def checkEof: (Input[A] => IterateeT[X, A, F, B]) => IterateeT[X, E, F, StepT[X, A, F, B]] = k =>
      isEof[X, E, F] flatMap {
        eof =>
          if (eof) done(scont(k), eofInput)
          else step(k)
      }
    def step: (Input[A] => IterateeT[X, A, F, B]) => IterateeT[X, E, F, StepT[X, A, F, B]] = k =>
      this flatMap (a => k(elInput(a)) >>== loop)
    loop
  }

  def zip[B](other: IterateeT[X, E, F, B])(implicit F: Monad[F]): IterateeT[X, E, F, (A, B)] = {
    def step[Z](i: IterateeT[X, E, F, Z], in: Input[E]) =
      IterateeTMonadTrans[X, E].liftM(i.foldT[(Either[X, Option[(Z, Input[E])]], IterateeT[X, E, F, Z])](
        cont = k => F.pure((Right(None), k(in)))
        , done = (a, x) => F.pure((Right(Some((a, x))), done(a, x)))
        , err = e => F.pure((Left(e), err(e)))
      ))
    def loop(x: IterateeT[X, E, F, A], y: IterateeT[X, E, F, B])(in: Input[E]): IterateeT[X, E, F, (A, B)] = in(
      el = _ =>
        step(x, in) flatMap {
          case (a, xx) =>
            step(y, in) flatMap {
              case (b, yy) =>
                (a, b) match {
                  case (Left(e), _)                                => err(e)
                  case (_, Left(e))                                => err(e)
                  case (Right(Some((a, e))), Right(Some((b, ee)))) => done((a, b), if (e.isEl) e else ee)
                  case _                                           => cont(loop(xx, yy))
                }
            }
        }
      , empty = cont(loop(x, y))
      , eof = (x >>== enumEofT(e => err(e))) flatMap (a => (y >>== enumEofT(e => err(e))) map (b => (a, b)))
    )
    cont(loop(this, other))
  }
}

object IterateeT extends IterateeTs

trait IterateeTLow1 {
  implicit def IterateeTMonad[X, E, F[_]](implicit F0: Monad[F]) = new IterateeTMonad[X, E, F] {
    implicit def F = F0
  }
  implicit def EnumeratorTSemigroup[X, E, F[_], A](implicit F0: Bind[F]) = new EnumeratorTSemigroup[X, E, F, A] {
    implicit def F = F0
  }
}

trait IterateeTLow0 extends IterateeTLow1 {
  self: IterateeTs =>

  implicit def IterateeTLiftIO[X, E, F[_]](implicit lio: LiftIO[F], m: Monad[F]): LiftIO[({type λ[α] = IterateeT[X, E, F, α]})#λ] = {
    new LiftIO[({type λ[α] = IterateeT[X, E, F, α]})#λ] {
      def liftIO[A](ioa: IO[A]) = IterateeTMonadTrans[X, E].liftM(lio.liftIO(ioa))
    }
  }

  implicit def EnumeratorTMonoid[X, E, F[_], A](implicit F0: Bind[F] with Pointed[F]) = new EnumeratorTMonoid[X, E, F, A] {
    implicit def F = F0
  }
}

trait IterateeTs extends IterateeTLow0 {
  type Iter[E, F[_], A] = IterateeT[Unit, E, F, A]

  def apply[X, E, F[_], A](s: F[StepT[X, E, F, A]]): IterateeT[X, E, F, A] =
    iterateeT(s)

  def iterateeT[X, E, F[_], A](s: F[StepT[X, E, F, A]]): IterateeT[X, E, F, A] = new IterateeT[X, E, F, A] {
    val value = s
  }

  def cont[X, E, F[_] : Pointed, A](c: Input[E] => IterateeT[X, E, F, A]): IterateeT[X, E, F, A] =
    iterateeT(Pointed[F].pure(StepT.scont(c)))

  def done[X, E, F[_] : Pointed, A](d: => A, r: => Input[E]): IterateeT[X, E, F, A] =
    iterateeT(Pointed[F].pure(StepT.sdone(d, r)))

  def err[X, E, F[_] : Pointed, A](e: => X): IterateeT[X, E, F, A] =
    iterateeT(Pointed[F].pure(StepT.serr(e)))

  implicit def IterateeTMonadTrans[X, E]: MonadTrans[({type λ[α[_], β] = IterateeT[X, E, α, β]})#λ] = new MonadTrans[({type λ[α[_], β] = IterateeT[X, E, α, β]})#λ] {
    def hoist[M[_], N[_]](f: M ~> N) = new (({type f[x] = IterateeT[X, E, M, x]})#f ~> ({type f[x] = IterateeT[X, E, N, x]})#f) {
      val M: Functor[M] = sys.error("Need to change signature of hoist to support IterateeTMonadTrans") // TODO
      def apply[A](fa: IterateeT[X, E, M, A]): IterateeT[X, E, N, A] = sys.error("not implemented")
    } 
    
//    def hoist[M[_], N[_]](f: M ~> N) = new (({type f[x] = LazyEitherT[Z, M, x]})#f ~> ({type f[x] = LazyEitherT[Z, N, x]})#f) {
//          def apply[A](fa: LazyEitherT[Z, M, A]): LazyEitherT[Z, N, A] = LazyEitherT(f.apply(fa.runT))
//        }
//
    def liftM[G[_] : Monad, A](ga: G[A]): IterateeT[X, E, G, A] =
      iterateeT(Monad[G].map(ga)((x: A) => StepT.sdone[X, E, G, A](x, emptyInput)))
  }

  /* TODO

  implicit def IterateeTMonadIO[X, E, F[_]](implicit mio: MonadIO[F]): MonadIO[({type λ[α] = IterateeT[X, E, F, α]})#λ] = {
    implicit val l = mio.liftIO
    implicit val m = mio.monad
    MonadIO.monadIO[({type λ[α] = IterateeT[X, E, F, α]})#λ]
  }*/

  /**
   * An iteratee that writes input to the output stream as it comes in.  Useful for debugging.
   */
  def putStrTo[X, E](os: java.io.OutputStream)(implicit s: Show[E]): IterateeT[X, E, IO, Unit] = {
    def write(e: E) = IO(os.write(s.shows(e).getBytes))
    foldM(())((_: Unit, e: E) => write(e))
  }

  /**An iteratee that consumes the head of the input **/
  def head[X, E, F[_] : Pointed]: IterateeT[X, E, F, Option[E]] = {
    def step(s: Input[E]): IterateeT[X, E, F, Option[E]] =
      s(empty = cont(step)
        , el = e => done(Some(e), emptyInput[E])
        , eof = done(None, eofInput[E])
      )
    cont(step)
  }

  def headDoneOr[X, E, F[_] : Monad, B](b: => B, f: E => IterateeT[X, E, F, B]): IterateeT[X, E, F, B] = {
    head[X, E, F] flatMap {
      case None => done(b, eofInput)
      case Some(a) => f(a)
    }
  }

  /**An iteratee that returns the first element of the input **/
  def peek[X, E, F[_] : Pointed]: IterateeT[X, E, F, Option[E]] = {
    def step(s: Input[E]): IterateeT[X, E, F, Option[E]]
    = s(el = e => done(Some(e), s),
      empty = cont(step),
      eof = done(None, eofInput[E]))
    cont(step)
  }

  def peekDoneOr[X, E, F[_] : Monad, B](b: => B, f: E => IterateeT[X, E, F, B]): IterateeT[X, E, F, B] = {
    peek[X, E, F] flatMap {
      case None => done(b, eofInput)
      case Some(a) => f(a)
    }
  }

  /**An iteratee that skips the first n elements of the input **/
  def drop[X, E, F[_] : Pointed](n: Int): IterateeT[X, E, F, Unit] = {
    def step(s: Input[E]): IterateeT[X, E, F, Unit] =
      s(el = _ => drop(n - 1),
        empty = cont(step),
        eof = done((), eofInput[E]))
    if (n == 0) done((), emptyInput[E])
    else cont(step)
  }

  /**
   * An iteratee that skips elements while the predicate evaluates to true.
   */
  def dropWhile[X, E, F[_] : Pointed](p: E => Boolean): IterateeT[X, E, F, Unit] = {
    def step(s: Input[E]): IterateeT[X, E, F, Unit] =
      s(el = e => if (p(e)) dropWhile(p) else done((), s),
        empty = cont(step),
        eof = done((), eofInput[E]))
    cont(step)
  }

  /**
   * An iteratee that skips elements until the predicate evaluates to true.
   */
  def dropUntil[X, E, F[_] : Pointed](p: E => Boolean): IterateeT[X, E, F, Unit] = dropWhile(!p(_))

  def fold[X, E, F[_] : Pointed, A](init: A)(f: (A, E) => A): IterateeT[X, E, F, A] = {
    def step(acc: A): Input[E] => IterateeT[X, E, F, A] = s =>
      s(el = e => cont(step(f(acc, e))),
        empty = cont(step(acc)),
        eof = done(acc, eofInput[E]))
    cont(step(init))
  }

  def foldM[X, E, F[_], A](init: A)(f: (A, E) => F[A])(implicit m: Monad[F]): IterateeT[X, E, F, A] = {
    def step(acc: A): Input[E] => IterateeT[X, E, F, A] = s =>
      s(el = e => IterateeTMonadTrans[X, E].liftM(f(acc, e)) flatMap (a => cont(step(a))),
        empty = cont(step(acc)),
        eof = done(acc, eofInput[E]))
    cont(step(init))
  }

  /**
   * An iteratee that counts and consumes the elements of the input
   */
  def length[X, E, F[_] : Pointed]: IterateeT[X, E, F, Int] = fold(0)((a, _) => a + 1)

  /**
   * An iteratee that checks if the input is EOF.
   */
  def isEof[X, E, F[_] : Pointed]: IterateeT[X, E, F, Boolean] = cont(in => done(in.isEof, in))
}

//
// Type class implementation traits
//

private[scalaz] trait IterateeTMonad[X, E, F[_]] extends Monad[({type λ[α] = IterateeT[X, E, F, α]})#λ] {
  implicit def F: Monad[F]

  def pure[A](a: => A) = StepT.sdone(a, emptyInput).pointI
  override def map[A, B](fa: IterateeT[X, E, F, A])(f: (A) => B): IterateeT[X, E, F, B] = fa map f
  def bind[A, B](fa: IterateeT[X, E, F, A])(f: A => IterateeT[X, E, F, B]): IterateeT[X, E, F, B] = fa flatMap f
}
