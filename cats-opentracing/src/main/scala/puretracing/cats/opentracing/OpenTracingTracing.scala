package puretracing.cats.opentracing

import cats.effect.Sync
import puretracing.api.{Tracer, TracingValue}

/**
  * Open tracing module, depends on open tracing java
  */
class OpenTracingTracing[F[_]](tracer: io.opentracing.Tracer)(implicit F: Sync[F]) extends Tracer[F] {
  override type Span = io.opentracing.Span

  override def startRootSpan(operationName: String): F[Span] =
    F.delay(tracer.buildSpan(operationName).start())

  override def startChild(span: Span, operationName: String): F[Span] =
    F.delay(tracer.buildSpan(operationName).asChildOf(span).start())

  override def finish(span: Span): F[Unit] = F.delay(span.finish())

  override def setTag(span: Span, key: String, v: TracingValue): F[Unit] =
    v match {
      case TracingValue.StringTracingValue(value) => F.delay(span.setTag(key, value))
      case TracingValue.NumberTracingValue(value) => F.delay(span.setTag(key, value))
      case TracingValue.BooleanTracingValue(value) => F.delay(span.setTag(key, value))
    }

  override def log(span: Span, fields: Seq[(String, TracingValue)]): F[Unit] = {
    val jFields = new java.util.HashMap[String, Any]()
    fields.foreach {
      case (key, TracingValue.StringTracingValue(value)) => jFields.put(key, value)
      case (key, TracingValue.NumberTracingValue(value)) => jFields.put(key, value)
      case (key, TracingValue.BooleanTracingValue(value)) => jFields.put(key, value)
    }
    F.delay(span.log(jFields))
  }

  override def setBaggageItem(span: Span, key: String, value: String): F[Unit] = F.delay(span.setBaggageItem(key, value))

  override def getBaggageItem(span: Span, key: String): F[Option[String]] = F.delay(Option(span.getBaggageItem(key)))
}