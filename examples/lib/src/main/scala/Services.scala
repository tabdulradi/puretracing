import cats.data.ReaderT
import cats.{Applicative, Functor, ~>}
import cats.effect.{Bracket, IO}
import cats.syntax.all._
import cats.instances.list._
import puretracing.api.Propagation
import puretracing.cats.dsl._

/**
  * Tracing Aware Algebra
  */
class FooAlgebra[F[_]: Propagation](algebra: BarAlgebra[F]) {

  def foo()(implicit F: Bracket[F, Throwable]): F[Int] =
    inChildSpan[F]("foo", "user_id" -> 3) { span =>
      span.log("event" -> "foo happened") *>
        (1 to 21).toList.traverse(_ => algebra.bar()).map(_.sum)
    }
}

/**
  * Tracing was not in mind when building this algebra
  * but thanks to Tagless Final it could propagate the context
  */
class BarAlgebra[F[_]: Functor](algebra: BazAlgebra[F]) {
  def bar()(implicit F: Bracket[F, Throwable]): F[Int] =
    algebra.baz().map(_  + 1)
}

/**
  * Tracing aware, also logs.
  */
class BazAlgebra[F[_]: Applicative: Propagation] {

  def baz()(implicit F: Bracket[F, Throwable]): F[Int] =
    inChildSpan[F]("baz") { span =>
      for {
        _ <- span.log("baz" -> 1)
        res <- 1.pure[F]
      } yield res
    }

}

// Console is defined for IO only, we should be able to derive instance for Tracing IO
trait Console[F[_]] {
  def println(a: Any): F[Unit]

  final def mapK[G[_]](f: F ~> G): Console[G] =
    a => f(println(a))
}
object Console {
  def apply[F[_]](implicit F: Console[F]) = F
  implicit val ioConsoleImpl: Console[IO] = (a: Any) => IO(println(a))
  implicit def readerTImpl[F[_], A](implicit console: Console[F]): Console[ReaderT[F, A, ?]] =
    a => ReaderT.liftF(console.println(a))
}
