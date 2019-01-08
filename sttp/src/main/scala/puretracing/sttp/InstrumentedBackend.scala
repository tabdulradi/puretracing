package puretracing.sttp

import com.softwaremill.sttp.{MonadError, Request, Response, SttpBackend, monadSyntax}
import monadSyntax._
import puretracing.api.Propagation

/**
  * Creates child span to represent the http request
  * Also adds trace id to the outgoing request headers
  */
object InstrumentedBackend {
  def apply[R[_], S](inner: SttpBackend[R, S])(implicit tracing: Propagation[R]): SttpBackend[R, S] =
    instance(inner, tracing)(
      req => s"${req.method.m}-${req.uri.host}${req.uri.port.map(p => s":$p").getOrElse("")}",
      (_, _) => inner.responseMonad.unit(()),
      (_, _, _) => inner.responseMonad.unit(()),
    )

  def instance[R[_], S](inner: SttpBackend[R, S], tracing: Propagation[R])(
    operationName: Request[_, S] => String,
    instrumentation: (Request[_, S], tracing.Span) => R[Unit],
    logError: (Throwable, Request[_, S], tracing.Span) => R[Unit]
  ): SttpBackend[R, S] =
    new SttpBackend[R, S] {
      val delegate: SttpBackend[R, S] = new HeaderPropagationBackend(inner)(tracing)
      implicit val ME: MonadError[R] = inner.responseMonad

      override def send[T](request: Request[T, S]): R[Response[T]] =
        for {
          parent <- tracing.getSpan()
          span <- tracing.startChild(parent, operationName(request))
          _ <- instrumentation(request, span)
          response <- sendAndCloseSpan(request, span)
        } yield response

      override def close(): Unit = delegate.close()
      override def responseMonad: MonadError[R] = delegate.responseMonad

      def sendAndCloseSpan[T](request: Request[T, S], span: tracing.Span): R[Response[T]] = {
        def finalizer = tracing.finish(span) // def because R might be something eager, like Future

        val right: R[Either[Throwable, Response[T]]] = delegate.send(request).flatMap(a => finalizer.map(_ => Right(a)))
        val attempt = responseMonad.handleError(right) { case err =>
          logError(err, request, span).flatMap(_ => finalizer.map(_ => Left(err)))
        }

        attempt.flatMap(_.fold(
          e => ME.error(e),
          res => ME.unit(res)
        ))
      }
    }
}
