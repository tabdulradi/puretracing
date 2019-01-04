import cats.data.ReaderT
import cats.{Applicative, Functor, ~>, FlatMap}
import cats.effect.{Bracket, IO}
import cats.syntax.all._
import cats.instances.list._
import puretracing.api.Propagation
import puretracing.cats.dsl._


/**
  * Tracing Aware Algebra
  */
class FooAlgebra[F[_]: Propagation](algebra: BarAlgebra[F], http: InstrumentedHttpClient[F]) {

  def foo()(implicit F: Bracket[F, Throwable]): F[Int] =
    inChildSpan[F]("foo", "user_id" -> 3) { span =>
      span.log("event" -> "foo happened") *>
        http.request("GET", "127.0.0.1") *>
        http.request("GET", "127.0.0.2") *>
        inChildSpan[F]("inner") { _ => http.request("POST", "127.0.0.3/lol") } *>
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

// Example how to instrument an http client to propagate headers
class InstrumentedHttpClient[F[_]: FlatMap](implicit console: Console[F], tracer: Propagation[F]) {
  import cats.syntax.all._

  def request(method: String, url: String): F[Unit] =
    for {
      span <- tracer.currentSpan
      headers <- tracer.export(span)
      // Simulate http request
      _ <- Console[F].println(s"┌$method $url HTTP/1.1")
      _ <- Console[F].println(headers.map { case (k,v) => s"$k: $v" }.mkString("│", " \n", ""))
      _ <- Console[F].println(s"│Keep-Alive: 300")
      _ <- Console[F].println(s"└Connection: keep-alive")
    } yield ()
}
