package puretracing.cats.instances

import cats.{Applicative, MonadError}
import cats.data.StateT
import cats.effect.{Bracket, ExitCase, Sync}
import cats.effect.syntax.bracket._
import cats.syntax.flatMap._
import cats.syntax.functor._
import puretracing.api.{Propagation, Tracer, TracingValue}

class StateTPropagation[F[_]: Applicative, E](val tracer: Tracer[F]) {
  type Effect[A] = StateT[F, tracer.Span, A]
  val Effect = StateT

  def runWithRootSpan[A, E](operationName: String, upstreamSpan: tracer.Headers)
                           (thunk: Effect[A])(implicit F: Sync[F]): F[A] =
    for {
      span <- tracer.startRootSpan(operationName, upstreamSpan)
      res <- thunk.run(span).guarantee(tracer.finish(span))
    } yield res._2

  implicit val propagationInstance: Propagation[Effect] =
    new Propagation[Effect] {
      override type Span = tracer.Span

      override def getSpan(): Effect[Span] = Effect.get

      override def setSpan(span: Span): Effect[Unit] =
        Effect.set()
//        Effect.set(span)


      override def startRootSpan(operationName: String, upstreamSpan: Headers): Effect[Span] =
        Effect.liftF(tracer.startRootSpan(operationName, upstreamSpan))

      override def export(span: Span): Effect[Headers] = 
        Effect.liftF(tracer.export(span))

      override def startChild(span: Span, operationName: String): Effect[Span] =
        Effect.liftF(tracer.startChild(span, operationName))

      override def finish(span: Span): Effect[Unit] =
        Effect.liftF(tracer.finish(span))

      override def setTag(span: Span, key: String, value: TracingValue): Effect[Unit] =
        Effect.liftF(tracer.setTag(span, key, value))

      override def log(span: Span, fields: Seq[(String, TracingValue)]): Effect[Unit] =
        Effect.liftF(tracer.log(span, fields))

      override def setBaggageItem(span: Span, key: String, value: String): Effect[Unit] =
        Effect.liftF(tracer.setBaggageItem(span, key, value))

      override def getBaggageItem(span: Span, key: String): Effect[Option[String]] =
        Effect.liftF(tracer.getBaggageItem(span, key))
    }

  implicit def effectSyncInstance(implicit F: Sync[F]): Sync[Effect] = cats.effect.Sync.catsStateTSync
}
