import cats.effect.{ExitCode, IO, IOApp}
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import puretracing.cats.instances.StateTPropagation
import puretracing.cats.opentracing.OpenTracingTracing
import io.jaegertracing.Configuration, Configuration.SamplerConfiguration, Configuration.ReporterConfiguration
import puretracing.sttp.InstrumentedBackend

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val tracer =
      new Configuration("sttp-example")
        .withSampler(SamplerConfiguration.fromEnv().withType("const").withParam(1))
        .withReporter(ReporterConfiguration.fromEnv().withLogSpans(true))
        .getTracer

    val tracing = new StateTPropagation(new OpenTracingTracing[IO](tracer))
    import tracing.propagationInstance
    implicit val backend = InstrumentedBackend[tracing.Effect, Nothing](AsyncHttpClientCatsBackend())

    tracing.runWithRootSpan("main", Map.empty) {  // typically called by an http framework middleware
      sttp
        .body(Map("name" -> "John", "surname" -> "doe"))
        .post(uri"https://httpbin.org/post")
        .send()
        .flatMapF(_.body.fold(
          r => IO(println(r)),
          r => IO(Console.err.println(r))
        ))
        .map(_ => ExitCode.Success)
    }.guarantee(IO(tracer.close())).guarantee(IO(backend.close()))
  }
}
