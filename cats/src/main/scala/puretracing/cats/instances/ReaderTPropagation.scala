package puretracing.cats.instances

import cats.Applicative
import cats.data.ReaderT
import puretracing.api.{Propagation, Tracer, TracingValue}

class ReaderTPropagation[F[_]: Applicative](val tracer: Tracer[F]) {
  type Effect[A] = ReaderT[F, tracer.Span, A]

  implicit val readerTPropagationInstance: Propagation[Effect] =
    new Propagation[Effect] {
      override type Span = tracer.Span

      override def currentSpan(): Effect[Span] =
        ReaderT.ask[F, Span]

      override def useSpanIn[A](span: Span)(fa: Effect[A]): Effect[A] =
        ReaderT.local[F, A, Span](_ => span)(fa)

      override def startRootSpan(operationName: String, upstreamSpan: Headers): Effect[Span] = 
        ReaderT.liftF(tracer.startRootSpan(operationName, upstreamSpan))

      override def export(span: Span): Effect[Headers] = 
        ReaderT.liftF(tracer.export(span))

      override def startChild(span: Span, operationName: String): Effect[Span] =
        ReaderT.liftF(tracer.startChild(span, operationName))

      override def finish(span: Span): Effect[Unit] =
        ReaderT.liftF(tracer.finish(span))

      override def setTag(span: Span, key: String, value: TracingValue): Effect[Unit] =
        ReaderT.liftF(tracer.setTag(span, key, value))

      override def log(span: Span, fields: Seq[(String, TracingValue)]): Effect[Unit] =
        ReaderT.liftF(tracer.log(span, fields))

      override def setBaggageItem(span: Span, key: String, value: String): Effect[Unit] =
        ReaderT.liftF(tracer.setBaggageItem(span, key, value))

      override def getBaggageItem(span: Span, key: String): Effect[Option[String]] =
        ReaderT.liftF(tracer.getBaggageItem(span, key))

    }
}
