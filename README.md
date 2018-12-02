# Pure Tracing
A POC to provide an opentracing equivalent standard for pure applications.
This repo contains the proposed interfaces, cats implementation, and few example applications.

## Run the examples
```bash
sbt run examplePrintlnTracing/run
```
This runs application with dummy println-based backed, just to see how the context get's propagated.

If you want see a more involved example, run the open tracing example, but first we need `Jaeger` instance running
```bash
docker run -d --name jaeger \
  -e COLLECTOR_ZIPKIN_HTTP_PORT=9411 \
  -p 5775:5775/udp \
  -p 6831:6831/udp \
  -p 6832:6832/udp \
  -p 5778:5778 \
  -p 16686:16686 \
  -p 14268:14268 \
  -p 9411:9411 \
  jaegertracing/all-in-one:1.8
```  
then
```bash
sbt run exampleOpenTracing/run
```

once the program finished, navigate to [localhost:16686](localhost:16686/), then from the first drop down menu select "pure-application", then click find traces.
 
## Todo
- [ ] Add `inject` & `extract` methods to Tracer
- [ ] Integrate with http-client library to propagation outgoing http calls
- [ ] Integrate with http4s to propagate from incoming http calls
- [ ] AWS XRay implementation

## Proposal highlights
- `api` module with zero dependecies, contains `Tracer` interface and `Propagation` type-class
- `cats` module provides ergonomic dsl, and `Propagation` implementation using `Reader` monad.
- `cats-opentracing` module provides `Tracer` implementation for any `opentracing.Tracer`
- `Propagation` is essentially `ApplicativeLocal`, but hides the Environment type param for ergonomics. 
- `Tracer#Span` get's set by each implementation, allows integration with non-opentracing like AWS XRay.