# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

TargetGen is a Java/Maven command-line tool that generates Eclipse `.target` files for a set of components from a "delivery report" plus a list of p2 update site repositories. It is custom-built for a specific in-house workflow and is not a general-purpose target generator. A known limitation is that it does not compute required units from dependencies the way Eclipse does, so generated targets may include extra units.

## Build / run / test

Maven with JDK 17 or 21 (Lombok 1.18.38 is needed for Java 25). The build runs `jaxb-maven-plugin` twice during `generate-sources`: one execution per XSD, into separate output directories so each schema lands in its own package. The generated packages are `in.adarshr.targetgen.schema.input` and `in.adarshr.targetgen.schema.output`. JAXB generation must run before the project will compile from an IDE or the CLI.

```
mvn -B package --file pom.xml             # full build: JAXB gen + tests + assembly
mvn generate-sources                      # only regenerate JAXB after schema edits
mvn test                                  # run JUnit 5 tests
mvn -Dtest=RepoUrlResolverTest test       # run a single test class
```

`package` produces a fat jar via `maven-assembly-plugin` (`jar-with-dependencies`) with main class `in.adarshr.targetgen.TargetGen`. The CLI accepts:

```
java -jar target/targetgen-jar-with-dependencies.jar \
    --input input/input.xml \
    --schema schema/input.xsd \
    --output output
```

All three flags have defaults that match the project layout, so running with no args still works as long as the working directory is the project root. `--help` prints the full options list.

The CI workflow at `.github/workflows/maven.yml` builds with Temurin JDK 17 on push/PR to `master`.

## High-level architecture

The pipeline lives in `TargetGenApp.run()`. `TargetGen.main` only parses CLI args via commons-cli and then calls `TargetGenApp`. Each step is a small instance service in the `service` package — no static utility bags. Read the steps in this order; most service classes only make sense in the context of the pipeline.

1. **Input parsing.** `JaxbUtils.unmarshalAndValidate` reads the input XML and validates it against the input XSD into a JAXB-generated `ComponentInfo`.
2. **Component resolution.** A `ComponentResolver` (interface in `service/`) turns `ComponentInfo` into `List<ResolvedComponent>`. Two implementations exist: `InputComponentResolver` derives repo URLs from the input XML alone; `ReportComponentResolver` overlays the delivery report. `TargetGenApp.chooseComponentResolver` picks one based on `componentInfo.isCreateBasedOnReport()`. **All `$GROUP$` / `$ARTIFACT$` / `$VERSION$` placeholder substitution lives in a single class — `RepoUrlResolver` — used by both implementations.** When changing URL patterns, edit one place.
3. **Delivery report loading** (only on the report path). `DeliveryReportLoader` reads either a file path or a URL (decided by `URI` scheme inspection) and filters by the `reportExtensionToUse` field.
4. **Jar download + content.xml extraction.** `JarDownloader` downloads each distinct p2 jar URL in parallel and extracts the embedded XML as a string into a `Map<Repo, String>`.
5. **Unit parsing.** `ContentXmlParser` parses each XML into `List<Unit>` per `Repo`, building `Map<Repo, List<Unit>>`. DOCTYPE is disabled in the parser config (XXE-safe).
6. **Pipeline context assembly.** `PipelineContext` is an immutable record holding `componentInfo`, the list of `ResolvedComponent`, and the repo→units map. There is no mutable state passed between steps.
7. **Target build.** `TargetBuilder.buildTargets(ctx)` is a pure function: it iterates `ctx.components()` and for each calls `buildOneTarget(ctx, component)`. No shared scratch state. Returns `Map<String, Target>` keyed by output filename.
8. **Marshalling.** `TargetWriter` creates the output directory (idempotent) and writes each target file. Handles the JAXB marshal + the `<?pde version="3.8"?>` processing instruction injection inside `JaxbUtils.marshalWithInstruction`.

### Package layout

- `in.adarshr.targetgen` — `TargetGen` (CLI entry, parses args only) and `TargetGenApp` (orchestration).
- `in.adarshr.targetgen.model` — hand-written domain types: `Repo`, `Unit`, `Report` (mutable Lombok `@Data` classes), plus the immutable `PipelineContext` and `ResolvedComponent` records. Replaces the older `bo` / `dto` split.
- `in.adarshr.targetgen.schema.input` / `.schema.output` — JAXB-generated. Do not edit; regenerate from XSDs in `schema/`.
- `in.adarshr.targetgen.service` — `ComponentResolver` interface plus `InputComponentResolver`, `ReportComponentResolver`, `RepoUrlResolver`, `DeliveryReportLoader`, `JarDownloader`, `ContentXmlParser`, `TargetWriter`. All instance classes; testable in isolation.
- `in.adarshr.targetgen.build` — `TargetBuilder`. Pure function over `PipelineContext`.
- `in.adarshr.targetgen.exception` — `TargetGenException`. Single catch site is in `TargetGen.main`.
- `in.adarshr.targetgen.utils` — `JaxbUtils` (the only true stateless utility left).

### Key cross-cutting things to know

- Two distinct JAXB models coexist: `schema.input.*` (read-only, input side) and `schema.output.*` (built by `TargetBuilder`, then marshalled). The hand-written `model.Repo` / `model.Unit` types are the in-memory bridge between them and are intentionally not the same classes as the JAXB-generated `Repo` / `Unit`.
- Lombok is on the classpath at version 1.18.38. Model classes use `@Data` (with an `@EqualsAndHashCode(of = {"group", "artifact"})` override on `Repo` so deduplication ignores location/version). All logging uses `@Slf4j`. An IDE without the Lombok plugin will show spurious errors.
- Logging is SLF4J + Logback; configuration in `src/main/resources/logback.xml`.
- Tests live under `src/test/java` (standard layout). The `maven-surefire-plugin` is wired up and the test dep is `junit-jupiter` (the bundle, not just `-api`), so `mvn test` actually runs them. Current coverage is `RepoUrlResolverTest`; the other services are isolated enough to be unit-tested similarly.
