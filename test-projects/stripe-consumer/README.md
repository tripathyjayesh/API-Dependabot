# Stripe consumer migration fixture

This is a separate Spring Boot Java 21 consumer fixture for testing the main API Dependabot project. It models selected Stripe API behavior using local mock controllers; it makes no Stripe network calls and needs no Stripe secret key.

The baseline is `2025-08-27.basil`; the migration target is `2025-09-30.clover`. See [the release evidence and the exact five affected/five control operations](docs/stripe-release-notes.md). Both API snapshots are curated OpenAPI 3.0 excerpts rather than complete Stripe specifications.

## Run the fixture

From this directory, run the Spring Boot tests and then start the local server. These commands use the repository's bundled Maven and a separate local dependency cache:

```powershell
$root = (Resolve-Path '../..').Path
$mvn = Join-Path $root 'work/apache-maven-3.9.11/bin/mvn.cmd'
$mavenRepo = Join-Path $root 'work/.m2/stripe-consumer-repository'
& $mvn "-Dmaven.repo.local=$mavenRepo" "-Dmaven.compiler.fork=true" -f "$PWD/pom.xml" test
& $mvn "-Dmaven.repo.local=$mavenRepo" "-Dmaven.compiler.fork=true" -f "$PWD/pom.xml" spring-boot:run
```

If Maven is installed globally, use `mvn test` and `mvn spring-boot:run`. Controller routes are under `/consumer/stripe`, for example `GET /consumer/stripe/customers/cus_demo`.

## Run API Dependabot for a target version

Build the main API Dependabot JAR once from the root directory. Then from this fixture directory:

```powershell
./run-dependabot.ps1 -TargetVersion 2025-09-30.clover
```

The target version is explicit and maps to a checked-in contract snapshot. The script passes Basil as the old contract, Clover as the new contract, the Java consumer source, and a migration question to the existing Dependabot CLI. This default run performs deterministic diff/source retrieval only and makes no model call.

Recorded terminal runs and findings are in [test observations](docs/test-observations.md); the raw console transcript is linked there.

For an evidence-cited live Vanilla RAG answer, after setting the model environment variables described in the root README, add `-LiveAnswer`. That makes a billable model request. The user question can be supplied with `-Question '...'`.

## Upgrade the consumer

The checked-in Java controller is deliberately the Basil consumer: it reads `iterations`, top-level promotion-code `coupon`, expanded `Discount.coupon`, Checkout Session `currencyConversion`, and assumes the Basil classic billing default. After capturing Dependabot's findings, migrate those call sites to `duration`, `promotion.type` plus `promotion.coupon`, `Discount.source`, no `currency_conversion` read, and explicit `billing_mode.type=classic` if classic semantics are required. Then update local response/request records and tests to Clover shapes.

OpenAPI tells Dependabot about structural request/response changes; [the local release notes](docs/stripe-release-notes.md) provide documentation evidence for behavior/default semantics that schema diff cannot determine on its own.
