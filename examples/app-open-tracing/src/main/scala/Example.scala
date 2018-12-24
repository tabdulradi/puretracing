import cats.effect.{ExitCode, IO, IOApp}
import puretracing.cats.instances.ReaderTPropagation
import puretracing.cats.opentracing.OpenTracingTracing
import io.jaegertracing.Configuration, Configuration.SamplerConfiguration, Configuration.ReporterConfiguration

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val tracer =
      new Configuration("pure-example")
        .withSampler(SamplerConfiguration.fromEnv().withType("const").withParam(1))
        .withReporter(ReporterConfiguration.fromEnv().withLogSpans(true))
        .getTracer

    val tracing = new ReaderTPropagation(new OpenTracingTracing[IO](tracer))
    import tracing.Effect
    import tracing.readerTPropagationInstance

    val algebra = new FooAlgebra(new BarAlgebra(new BazAlgebra[Effect]), new InstrumentedHttpClient[Effect])
    val app = algebra.foo().flatMap(Console[Effect].println)

    for { // Root span creation is typically job of an http framework middleware. We still need to implement inject and extract headers first
      root <- tracing.tracer.startRootSpan("main", Map.empty)
      _ <- app.run(root).guarantee(for {
        _ <- tracing.tracer.finish(root)
        _ <- IO(tracer.close())
      } yield ())
    } yield ExitCode.Success
  }
}
