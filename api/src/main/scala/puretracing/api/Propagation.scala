package puretracing.api

/**
  * Selected functionality from opentracing's Tracer as well as Span
  * Doesn't deal with context propagation
  */
trait Tracer[F[_]] {
  type Span
  type Headers = Map[String, String] // TODO: Make this param, perefebly on the methods not the whole trait

  def startRootSpan(operationName: String, upstreamSpan: Headers): F[Span]
  def export(span: Span): F[Headers]
  def startChild(span: Span, operationName: String): F[Span]
  def finish(span: Span): F[Unit]
  def setTag(span: Span, key: String, value: TracingValue): F[Unit]
  def log(span: Span, fields: Seq[(String, TracingValue)]): F[Unit]
  def setBaggageItem(span: Span, key: String, value: String): F[Unit]
  def getBaggageItem(span: Span, key: String): F[Option[String]]
}

/**
  * Deals with context propagation.
  * Low level, users should use cat's dsl
  */
trait Propagation[F[_]] extends Tracer[F] { // F could be ReaderT[IO]
  def currentSpan(): F[Span]
  def useSpanIn[A](span: Span)(fa: F[A]): F[A]
}

sealed trait TracingValue // Poor man's union types
object TracingValue {
  final case class StringTracingValue(value: String) extends TracingValue
  final case class NumberTracingValue(value: Number) extends TracingValue
  final case class BooleanTracingValue(value: Boolean) extends TracingValue
}
