inThisBuild(Seq(
  scalacOptions += "-Ypartial-unification",
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8")
))

lazy val api = project
lazy val cats = project.dependsOn(api).settings(libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-mtl-core" % "0.4.0",
  "org.typelevel" %% "cats-effect" % "1.0.0"
))
lazy val `cats-opentracing` = project.dependsOn(cats).settings(libraryDependencies += "io.opentracing" % "opentracing-api" % "0.31.0")
lazy val sttp = project.dependsOn(api).settings(libraryDependencies += "com.softwaremill.sttp" %% "core" % "1.5.2")

lazy val exampleLib = project.in(file("examples/lib")).dependsOn(cats).settings(libraryDependencies += "org.typelevel" %% "cats-effect" % "1.0.0")
lazy val exampleAppNoTrace = project.in(file("examples/app-no-trace")).dependsOn(exampleLib)
lazy val examplePrintlnTracing = project.in(file("examples/app-println-tracing")).dependsOn(exampleLib)
lazy val exampleOpenTracing = project.in(file("examples/app-open-tracing")).dependsOn(exampleLib, `cats-opentracing`)
  .settings(libraryDependencies += "io.jaegertracing" % "jaeger-client" % "0.32.0")
lazy val exampleSttpCats = project.in(file("examples/app-sttp")).dependsOn(sttp, `cats-opentracing`).settings(libraryDependencies ++= Seq(
  "com.softwaremill.sttp" %% "async-http-client-backend-cats" % "1.5.2",
  "io.jaegertracing" % "jaeger-client" % "0.32.0"
))
