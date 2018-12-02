import puretracing.cats.instances.ReaderTPropagation

object Main {
  def main(args: Array[String]): Unit = {
    val tracing = new ReaderTPropagation(new PrintLnTracing)
    import tracing.Effect
    import tracing.readerTPropagationInstance
    val algebra = new FooAlgebra(new BarAlgebra(new BazAlgebra[Effect]))
    val app = algebra.foo().flatMap(Console[Effect].println)
    tracing.tracer.startRootSpan("main").flatMap(app.run).unsafeRunSync()
  }
}

import cats.effect.IO
import cats.syntax.functor._
import puretracing.api.{Tracer, TracingValue}

import scala.util.Random

class PrintLnTracing extends Tracer[IO] {
  override type Span = List[String]

  private def rand(operationName: String): IO[String] =
    IO(Random.nextInt(10)).map(id => s"$operationName@$id")

  override def startRootSpan(operationName: String): IO[Span] = rand(operationName).map(List(_))
  override def startChild(span: Span, operationName: String): IO[Span] = rand(operationName).map(_ :: span)
  override def finish(span: Span): IO[Unit] = log(span, "finish")
  override def setTag(span: Span, key: String, value: TracingValue): IO[Unit] = log(span, s"tag($key -> $value)")
  override def log(span: Span, fields: Seq[(String, TracingValue)]): IO[Unit] = log(span, fields.map{case (k, v) => s"$k -> $v"}.mkString("log(", ",", ")"))
  override def setBaggageItem(span: Span, key: String, value: String): IO[Unit] = log(span, s"set($key -> $value)")
  override def getBaggageItem(span: Span, key: String): IO[Option[String]] = log(span, s"get($key)").as(None)

  private def log(span: Span, msg: String) = {
    val prefix = span.reverse.mkString("<",".", ">")
    IO(println(s"$prefix $msg"))
  }
}
