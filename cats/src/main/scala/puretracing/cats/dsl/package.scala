package puretracing.cats

import cats.{Applicative, Monad}
import cats.effect.{Bracket, IO}
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.instances.list._

import puretracing.api.{Propagation, Tracer, TracingValue}

package object dsl {
  def inChildSpan[F[_]]: ChildSpanPartiallyApplied[F] = new ChildSpanPartiallyApplied[F]

  implicit def tracingValueFromString(value: String): TracingValue = TracingValue.StringTracingValue(value)
  implicit def tracingValueFromBoolean(value: Boolean): TracingValue = TracingValue.BooleanTracingValue(value)
  implicit def tracingValueFromInt(value: Int): TracingValue = TracingValue.NumberTracingValue(value)
  implicit def tracingValueFromDouble(value: Double): TracingValue = TracingValue.NumberTracingValue(value)
  implicit def tracingValueFromBigDecimal(value: BigDecimal): TracingValue = TracingValue.NumberTracingValue(value)
}


class ChildSpanPartiallyApplied[F[_]] {
  def apply[A](operationName: String, tags: (String, TracingValue)*)(logic: SpanOps[F] => F[A])(
    implicit
    tracing: Propagation[F],
    M: Monad[F],
    E: Bracket[F, Throwable]
  ): F[A] = for {
    parent <- tracing.currentSpan()
    span <- tracing.startChild(parent, operationName)
    richSpan = spanOps(tracing)(span)
    fa <- tracing.useSpanIn(span)(richSpan.tag(tags: _*) *> logic(richSpan)).guarantee(tracing.finish(span))
  } yield fa

  private def spanOps(tracer: Tracer[F])(span: tracer.Span)(implicit F: Applicative[F]) = new SpanOps[F] {
    def tag(tags: (String, TracingValue)*): F[Unit] =
      tags.toList.traverse { case (k, v) => tracer.setTag(span, k, v) }.map(_ => ())

    def log(fields: (String, TracingValue)*): F[Unit] = tracer.log(span, fields)
    def set(key: String, value: String): F[Unit] = tracer.setBaggageItem(span, key, value)
    def get(key: String): F[Option[String]] = tracer.getBaggageItem(span, key)
  }
}

trait SpanOps[F[_]] {
  def tag(tags: (String, TracingValue)*): F[Unit]
  def log(fields: (String, TracingValue)*): F[Unit]
  def set(key: String, value: String): F[Unit]
  def get(key: String): F[Option[String]]
}
