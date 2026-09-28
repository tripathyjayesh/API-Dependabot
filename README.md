# API Dependabot

API Dependabot is a capstone MVP for detecting OpenAPI 3.x changes, identifying their impact in Java/Spring Boot Maven consumers, and producing evidence-backed migration assistance with GitHub review.

## Project status

Planning and architecture are documented in [DESIGN.md](DESIGN.md) and [PROJECT_PLAN.md](PROJECT_PLAN.md).

 The deterministic baseline compares local OpenAPI specs, analyzes Java source/test matches, and ranks relevant code and contract evidence for a question. 
 
 Vanilla RAG and the read-only ReAct agent are implemented as opt-in live-model paths.
 
  A six-case Promptfoo development-set comparison has completed; the current run is recorded below. It is not a held-out evaluation and does not establish a final architecture choice.

  The final test for the capstone submission is the Stripe Basil-to-Clover demo and tests where we check for the migration strategy from stripe [basil](https://docs.stripe.com/changelog#2025-07-30.basil) to stripe [clover](https://docs.stripe.com/changelog#2025-09-30.clover) which has some breaking changs also.

## Run the deterministic diff

Requirements: Java 21 and Maven 3.6.3 or later. Run commands from the project root. If Maven is not on `PATH` but the provided `work` folder is present, use its bundled Windows Maven distribution:

```powershell
mvn -Pecj-compiler package
java -jar target/api-dependabot-0.1.0-SNAPSHOT.jar --old=src/test/resources/specs/v1.yaml --new=src/test/resources/specs/v2-removed-operation.yaml
```

Run these commands from the cloned project root. Install Maven 3.6.3 or later if it is not already on `PATH`. The `ecj-compiler` profile provides a portable Java compiler path for Windows.

Append `--format=json` to any successful CLI mode to emit one JSON object to standard output. The Spring banner and startup logs are suppressed in this mode, so a script can parse stdout directly. For example:

```powershell
java -jar target/api-dependabot-0.1.0-SNAPSHOT.jar --old=src/test/resources/specs/v1.yaml --new=src/test/resources/specs/v2-removed-response-property.yaml --repo=src/test/resources/consumer-project --question="Where does the consumer read the removed widget name?" --format=json
```

The JSON includes a `schemaVersion`, `status`, `mode`, and the result fields for that mode. RAG answers include answer text and latency; Vanilla RAG includes retrieved evidence and evidence references; ReAct includes its tool trace and call count. Invalid CLI inputs and runtime exceptions are returned with `status: "error"` when JSON mode is enabled. Markdown remains the default.

## Run the complete validation and evaluation

Requirements: Java 21 JDK, Git, Node.js 22.22.0 or later with npm, and an `OPENAI_API_KEY`. The script builds and tests the application, optionally checks out and searches a public GitHub repository, then runs all six shared cases with both Vanilla RAG and ReAct. The model evaluation makes live calls. From the project root in PowerShell:

```powershell
$env:OPENAI_API_KEY = "your-api-key"
$env:SPRING_AI_MODEL_CHAT = "openai"
.\scripts\run-capstone-validation.ps1 -GitHubRepository "https://github.com/spring-projects/spring-petclinic"
```

Omit `-GitHubRepository ...` to skip the optional remote checkout smoke check. For a private GitHub repository, set `GITHUB_TOKEN` in the same PowerShell session. The script runs `mvn verify` with the portable `ecj-compiler` profile, then writes the evaluation outputs below. If one or more assertions fail, it still creates the summary and exits with Promptfoo's nonzero status.

The shared six-case development set covers added/removed operations, a newly required request property, a removed response property, a renamed path parameter, and the Stripe Basil-to-Clover migration fixture. Promptfoo saves raw answers, judge scores, latency, retrieved evidence, and ReAct tool traces to `evaluation/promptfoo/results.json`; `evaluation/promptfoo/summary.json` contains an aggregate suitable for review. Raw result files are ignored by Git because they may contain repository excerpts. These development cases are not a held-out evaluation.

The command prints an OpenAPI diff as Markdown. The current deterministic checks cover added/removed operations, newly required request fields, removed top-level response properties, and renamed path parameters. For response properties, the added consumer-impact note currently handles direct component-schema references and top-level properties; nested or composed schemas need further coverage. AST-aware source matching remains future work.

To compare and search the sample Spring Boot consumer repository in one run, omit `--find` and terms are derived from the detected contract changes:

```powershell
java -jar target/api-dependabot-0.1.0-SNAPSHOT.jar --old=src/test/resources/specs/v1.yaml --new=src/test/resources/specs/v2-removed-response-property.yaml --repo=src/test/resources/consumer-project
```

The diff-to-search handoff derives changed property names plus Java bean accessors, changed path parameter names, and operation/path terms from added or removed operations. Explicit `--find=term1,term2` can still override automatic terms. Search is a case-sensitive lexical scan over `.java` lines with identifier boundaries; it includes `src/main` and `src/test`, returns exact line evidence, labels likely declarations, and skips `.git`, `target`, `build`, and `.idea` directories. This is not AST analysis: review the returned evidence before treating a match as a consumer call.

To retrieve the top evidence chunks for a natural-language question, add `--question` (and optionally `--top-k`, from 1 to 20):

```powershell
java -jar target/api-dependabot-0.1.0-SNAPSHOT.jar --old=src/test/resources/specs/v1.yaml --new=src/test/resources/specs/v2-removed-response-property.yaml --repo=src/test/resources/consumer-project --question="Where does the Java consumer read the widget name?" --top-k=3
```

Retrieval uses deterministic BM25-style lexical ranking over 25-line chunks with 5-line overlap. It indexes Java source, Java tests, `README.md`, and `pom.xml`, plus the current OpenAPI diff report. Each result retains its source and line range. CamelCase terms are split for matching, and a small fixed stop-word list is applied. No embeddings or model calls are involved in this baseline. Retrieval gold cases are in `evaluation/retrieval-cases.json`.

## Vanilla RAG answer (live model call)

The optional `--answer` flag takes the top retrieved chunks and deterministic OpenAPI diff, then makes exactly one model request for an evidence-cited recommendation. Without `--answer`, diff, source search, and retrieval remain offline and require no key.

For a live answer, configure the key and enable the OpenAI chat model in the current PowerShell session:

```powershell
$env:OPENAI_API_KEY = "your-api-key"
$env:SPRING_AI_MODEL_CHAT = "openai"
java -jar target/api-dependabot-0.1.0-SNAPSHOT.jar --old=src/test/resources/specs/v1.yaml --new=src/test/resources/specs/v2-removed-response-property.yaml --repo=src/test/resources/consumer-project --question="Where does the consumer read the removed widget name?" --top-k=3 --answer
```

The output contains a recommendation, evidence ID-to-file/line map, and request latency. The default model is `gpt-5-mini`; override it with `APIDEPENDABOT_MODEL`. This live request is billable and sends the diff plus retrieved chunks to the configured model provider. Offline mode is the default (`SPRING_AI_MODEL_CHAT=none`).

## ReAct answer (live tool-using agent)

Use the same API key and model configuration as Vanilla RAG, but pass `--react` instead of `--answer`:

```powershell
java -jar target/api-dependabot-0.1.0-SNAPSHOT.jar --old=src/test/resources/specs/v1.yaml --new=src/test/resources/specs/v2-removed-response-property.yaml --repo=src/test/resources/consumer-project --question="Where does the consumer read the removed widget name?" --react
```

The model can iteratively inspect the diff, search Java, read a bounded Java line range, and retrieve evidence. These are read-only tools scoped to the repository path passed in `--repo`; file reads are limited to Java files and 80 lines, and each answer has an eight-call tool budget. Output includes the final answer, each tool observation, the tool-call count, and total latency. This may make multiple model round-trips, so compare its quality, call count, and latency with Vanilla RAG using the same question and fixtures. Neither approach is declared the winner in advance.

## Scope

The MVP is limited to OpenAPI 3.x, Java/Spring Boot, Maven, and GitHub. The planned comparison is Vanilla RAG versus a ReAct tool-using agent on the same evaluation cases. The final architecture choice will be based on measured results.


### Stripe Basil-to-Clover demo and tests

The separate `test-projects/stripe-consumer` Spring Boot application is included in the capstone test path. Its controller tests exercise five unchanged control endpoints and the consumer assumptions affected by five selected Basil-to-Clover changes. The root validation script runs these fixture tests automatically after the main Maven verification.

For a focused live demo, first build the main application, then run these commands from the project root. The first is offline; the next two send live, billable requests. Configure `OPENAI_API_KEY` and `SPRING_AI_MODEL_CHAT=openai` before the live runs.

```powershell
mvn -Pecj-compiler package
./test-projects/stripe-consumer/run-dependabot.ps1 -TargetVersion 2025-09-30.clover -Approach offline
./test-projects/stripe-consumer/run-dependabot.ps1 -TargetVersion 2025-09-30.clover -Approach vanilla-rag
./test-projects/stripe-consumer/run-dependabot.ps1 -TargetVersion 2025-09-30.clover -Approach react
```

The live Vanilla RAG and ReAct runs use the same Stripe contract pair, repository, and question, making their evidence and migration recommendations easy to compare. For the full paired evaluation, run `npm run eval`; the Stripe case is part of the shared set and its results contribute to `evaluation/promptfoo/summary.json`.

the output form the CLI
```` 
Step 3/4: running the complete shared Vanilla RAG vs ReAct evaluation...

> eval
> promptfoo eval -c evaluation/promptfoo/config.yaml --no-cache --output evaluation/promptfoo/results.json

(node:37652) ExperimentalWarning: DecompressInterceptor is experimental and subject to change
(Use `node --trace-warnings ...` to show where the warning was created)
Cache is disabled.
Starting evaluation eval-ugt-2026-09-28T00:38:17
Running 12 test cases (up to 4 at a time)...
Evaluating [████████████████████████████████████████] 100% | 12/12 | Vanilla RAG "Answer thi" caseId=stripe-basil-clover-consumer-001

┌──────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┐
│ caseId               │ oldSpec              │ newSpec              │ repository           │ question             │ expectedSymbol       │ expectedAssessment   │ [Vanilla RAG] Answer │ [ReAct] Answer this  │
│                      │                      │                      │                      │                      │                      │                      │ this migration       │ migration question   │
│                      │                      │                      │                      │                      │                      │                      │ question using       │ using evidence from  │
│                      │                      │                      │                      │                      │                      │                      │ evidence from the    │ the supplied API and │
│                      │                      │                      │                      │                      │                      │                      │ supplied API and     │ Java repository:     │
│                      │                      │                      │                      │                      │                      │                      │ Java repository:     │ {{question}}         │
│                      │                      │                      │                      │                      │                      │                      │ {{question}}         │                      │
├──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│ removed-response-na… │ src/test/resources/… │ src/test/resources/… │ src/test/resources/… │ Where does the Java  │ getName()            │ The response         │ [PASS] Confirmed     │ [PASS] Short answer  │
│                      │                      │                      │                      │ consumer read the    │                      │ property name was    │ contract change      │ - The consumer code  │
│                      │                      │                      │                      │ removed widget name? │                      │ removed from GET     │ - The API removed    │ reads the widget     │
│                      │                      │                      │                      │                      │                      │ /widgets/{id}; the   │ the response         │ name via             │
│                      │                      │                      │                      │                      │                      │ sample Java service  │ property "name" from │ Widget.getName(),    │
│                      │                      │                      │                      │                      │                      │ calls                │ GET /widgets/{id}    │ which is called from │
│                      │                      │                      │                      │                      │                      │ Widget.getName(),    │ 200 application/json │ WidgetService.displ… │
│                      │                      │                      │                      │                      │                      │ but the sample does  │ [E1].                │ (src/main/java/demo… │
│                      │                      │                      │                      │                      │                      │ not show HTTP        │ Where the Java       │ and asserted in the  │
│                      │                      │                      │                      │                      │                      │ deserialization.     │ consumer reads the   │ unit test            │
│                      │                      │                      │                      │                      │                      │                      │ removed name         │ (src/test/java/demo… │
│                      │                      │                      │                      │                      │                      │                      │ - The consumer calls │                      │
│                      │                      │                      │                      │                      │                      │                      │ widget.getName() in  │                      │
│                      │                      │                      │                      │                      │                      │                      │ WidgetService.displ… │                      │
│                      │                      │                      │                      │                      │                      │                      │ (retu...             │                      │
├──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│ removed-operation-0… │ src/test/resources/… │ src/test/resources/… │ src/test/resources/… │ Which API operation  │ /widgets/{id}        │ GET /widgets/{id}    │ [PASS] Confirmed     │ [PASS] Short answer  │
│                      │                      │                      │                      │ was removed, and     │                      │ was removed and is a │ contract change      │ - Removed operation: │
│                      │                      │                      │                      │ what should a Java   │                      │ breaking contract    │ - The OpenAPI diff   │ GET /widgets/{id}    │
│                      │                      │                      │                      │ consumer check?      │                      │ change; callers of   │ shows the GET        │ (OpenAPI diff) [T1]. │
│                      │                      │                      │                      │                      │                      │ that operation need  │ /widgets/{id}        │ - In this Java       │
│                      │                      │                      │                      │                      │                      │ review.              │ operation was        │ repository I found   │
│                      │                      │                      │                      │                      │                      │                      │ removed (deleted)    │ no code that calls   │
│                      │                      │                      │                      │                      │                      │                      │ from the Widget API  │ that endpoint.       │
│                      │                      │                      │                      │                      │                      │                      │ spec, breaking       │ Search for           │
│                      │                      │                      │                      │                      │                      │                      │ backward             │ "widgets/" returned  │
│                      │                      │                      │                      │                      │                      │                      │ compatibility [E1].  │ no matches [T2], and │
│                      │                      │                      │                      │                      │                      │                      │ Possible consumer    │ the only             │
│                      │                      │                      │                      │                      │                      │                      │ impact (Java)        │ Widget-related code  │
│                      │                      │                      │                      │                      │                      │                      │ - Any Java code that │ is a local           │
│                      │                      │                      │                      │                      │                      │                      │ called the removed   │ model/service/...    │
│                      │                      │                      │                      │                      │                      │                      │ opera...             │                      │
├──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│ added-operation-001  │ src/test/resources/… │ src/test/resources/… │ src/test/resources/… │ Does adding POST     │ POST /widgets        │ Adding POST /widgets │ [PASS] Confirmed     │ [PASS] Short answer  │
│                      │                      │                      │                      │ /widgets break       │                      │ is generally         │ contract change      │ - No — adding POST   │
│                      │                      │                      │                      │ existing consumers?  │                      │ backward compatible  │ - A new endpoint     │ /widgets is an       │
│                      │                      │                      │                      │                      │                      │ for existing         │ POST /widgets was    │ additive,            │
│                      │                      │                      │                      │                      │                      │ consumers, though    │ added in v2.0.0.     │ backward-compatible  │
│                      │                      │                      │                      │                      │                      │ consumers may adopt  │ This is shown in the │ change and (based on │
│                      │                      │                      │                      │                      │                      │ the new operation.   │ provided OpenAPI     │ the repository       │
│                      │                      │                      │                      │                      │                      │                      │ diff summary [E1].   │ content) does not    │
│                      │                      │                      │                      │                      │                      │                      │ Confirmed            │ break existing       │
│                      │                      │                      │                      │                      │                      │                      │ compatibility        │ consumers of this    │
│                      │                      │                      │                      │                      │                      │                      │ statement            │ repo.                │
│                      │                      │                      │                      │                      │                      │                      │ - The diff record    │ What I confirmed     │
│                      │                      │                      │                      │                      │                      │                      │ explicitly states    │ from the repo        │
│                      │                      │                      │                      │                      │                      │                      │ “API changes are     │ - The OpenAPI diff   │
│                      │                      │                      │                      │                      │                      │                      │ backward compatible” │ shows a new          │
│                      │                      │                      │                      │                      │                      │                      │ [E1...               │ operation POST...    │
├──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│ required-request-fi… │ src/test/resources/… │ src/test/resources/… │ src/test/resources/… │ What must existing   │ category             │ The POST /widgets    │ [PASS] Confirmed     │ [PASS] Short answer  │
│                      │                      │                      │                      │ clients change when  │                      │ request now requires │ contract changes     │ - Confirmed: POST    │
│                      │                      │                      │                      │ creating a widget?   │                      │ category, so         │ (from the OpenAPI    │ /widgets now         │
│                      │                      │                      │                      │                      │                      │ existing create      │ diff)                │ requires the request │
│                      │                      │                      │                      │                      │                      │ clients must include │ - POST /widgets now  │ body to be           │
│                      │                      │                      │                      │                      │                      │ it; the sample       │ requires request     │ application/json and │
│                      │                      │                      │                      │                      │                      │ repository does not  │ Content-Type:        │ adds a new required  │
│                      │                      │                      │                      │                      │                      │ demonstrate a create │ application/json.    │ string property      │
│                      │                      │                      │                      │                      │                      │ request.             │ - POST /widgets      │ category. (OpenAPI   │
│                      │                      │                      │                      │                      │                      │                      │ request body has a   │ diff) [T2].          │
│                      │                      │                      │                      │                      │                      │                      │ new required         │ - Effect for clients │
│                      │                      │                      │                      │                      │                      │                      │ property "category"  │ creating widgets:    │
│                      │                      │                      │                      │                      │                      │                      │ (string).            │ they must send       │
│                      │                      │                      │                      │                      │                      │                      │ - The change is      │ Content-Type:        │
│                      │                      │                      │                      │                      │                      │                      │ flagged as breaking  │ application/jso...   │
│                      │                      │                      │                      │                      │                      │                      │ (backward inc...     │                      │
├──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│ renamed-path-parame… │ src/test/resources/… │ src/test/resources/… │ src/test/resources/… │ What changed in the  │ widgetId             │ The path parameter   │ [PASS] Confirmed     │ [PASS] Short answer  │
│                      │                      │                      │                      │ widget lookup path,  │                      │ changed from id to   │ contract change      │ - What changed: the  │
│                      │                      │                      │                      │ and what should a    │                      │ widgetId; the diff   │ (from the OpenAPI    │ GET widget lookup    │
│                      │                      │                      │                      │ generated client     │                      │ tool may normalize   │ diff)                │ path’s               │
│                      │                      │                      │                      │ maintainer review?   │                      │ this, so             │ - GET /widgets/{id}  │ path-parameter name  │
│                      │                      │                      │                      │                      │                      │ generated-client     │ was changed to GET   │ was renamed from     │
│                      │                      │                      │                      │                      │                      │ bindings warrant     │ /widgets/{widgetId}  │ {id} to {widgetId}   │
│                      │                      │                      │                      │                      │                      │ review.              │ — the path parameter │ (OpenAPI diff) [T1]. │
│                      │                      │                      │                      │                      │                      │                      │ name changed (no     │ - What a             │
│                      │                      │                      │                      │                      │                      │                      │ other spec           │ generated-client     │
│                      │                      │                      │                      │                      │                      │                      │ differences were     │ maintainer should    │
│                      │                      │                      │                      │                      │                      │                      │ reported) [E1].      │ review:              │
│                      │                      │                      │                      │                      │                      │                      │ Possible consumer    │ parameter-name       │
│                      │                      │                      │                      │                      │                      │                      │ impact and what a    │ bindings in the      │
│                      │                      │                      │                      │                      │                      │                      │ generated-client     │ generated client     │
│                      │                      │                      │                      │                      │                      │                      │ ma...                │ (method sign...      │
├──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┼──────────────────────┤
│ stripe-basil-clover… │ test-projects/strip… │ test-projects/strip… │ test-projects/strip… │ We are upgrading     │ currency_conversion  │ Existing request     │ [PASS] Confirmed     │ [PASS] Summary —     │
│                      │                      │                      │                      │ this Stripe consumer │                      │ migrations include   │ contract changes     │ confirmed contract   │
│                      │                      │                      │                      │ from                 │                      │ phases[].iterations  │ (from the OpenAPI    │ changes that affect  │
│                      │                      │                      │                      │ 2025-08-27.basil to  │                      │ to phases[].duration │ diff)                │ this repository      │
│                      │                      │                      │                      │ 2025-09-30.clover.   │                      │ and top-level        │ - POST               │ (with repo evidence) │
│                      │                      │                      │                      │ Which existing       │                      │ promotion-code       │ /subscription_sched… │ and the migration    │
│                      │                      │                      │                      │ request and response │                      │ coupon to            │ (request)            │ actions to take:     │
│                      │                      │                      │                      │ fields need          │                      │ promotion.type/prom… │   - Removed:         │ 1) POST              │
│                      │                      │                      │                      │ migration, and what  │                      │ Subscription         │ phases[].iterations  │ /subscription_sched… │
│                      │                      │                      │                      │ should we check      │                      │ Discount responses   │   - Added:           │ —                    │
│                      │                      │                      │                      │ about the new        │                      │ replace              │ phases[].duration    │ phases[].iterations  │
│                      │                      │                      │                      │ subscription billing │                      │ discounts[].coupon   │ (object) with        │ removed, replaced by │
│                      │                      │                      │                      │ default?             │                      │ with                 │ interval (string)    │ phases[].duration    │
│                      │                      │                      │                      │                      │                      │ discounts[].source,  │ and interval_count   │ (interval +          │
│                      │                      │                      │                      │                      │                      │ and Checkout S...    │ (integer)            │ interval_count)      │
│                      │                      │                      │                      │                      │                      │                      │   - Source: contract │ - Wh...              │
│                      │                      │                      │                      │                      │                      │                      │ diff [E2]. ...       │                      │
└──────────────────────┴──────────────────────┴──────────────────────┴──────────────────────┴──────────────────────┴──────────────────────┴──────────────────────┴──────────────────────┴──────────────────────┘
✓ Eval complete (ID: eval-ugt-2026-09-28T00:38:17)

Total Tokens: 13,946
  Grading: 13,946 (12,001 prompt, 1,945 completion, 660 reasoning)

Results:
  ✓ 12 passed (100%)
  0 failed (0%)
  0 errors (0%)
Duration: 6m 26s (concurrency: 4)

Writing output to evaluation/promptfoo/results.json
Step 4/4: summarizing pass rate, judge scores, latency, and ReAct tool use...
Case                                    | Approach     | Pass   | Judge  | Latency    | Tools
----------------------------------------+--------------+--------+--------+------------+------
removed-response-name-001               | ReAct        | PASS   | 1.00   | 35266 ms   | 8
removed-response-name-001               | Vanilla RAG  | PASS   | 1.00   | 9644 ms    | 0
removed-operation-001                   | Vanilla RAG  | PASS   | 1.00   | 17236 ms   | 0
removed-operation-001                   | ReAct        | PASS   | 1.00   | 29565 ms   | 8
added-operation-001                     | Vanilla RAG  | PASS   | 1.00   | 8053 ms    | 0
added-operation-001                     | ReAct        | PASS   | 1.00   | 34906 ms   | 8
required-request-field-001              | ReAct        | PASS   | 1.00   | 32447 ms   | 8
required-request-field-001              | Vanilla RAG  | PASS   | 1.00   | 9616 ms    | 0
renamed-path-parameter-001              | Vanilla RAG  | PASS   | 1.00   | 15199 ms   | 0
renamed-path-parameter-001              | ReAct        | PASS   | 1.00   | 28281 ms   | 4
stripe-basil-clover-consumer-001        | ReAct        | PASS   | 1.00   | 34486 ms   | 6
stripe-basil-clover-consumer-001        | Vanilla RAG  | PASS   | 1.00   | 30916 ms   | 0

Approach summary:
- ReAct: 6/6 passed; mean judge 1.00; median latency 33467 ms; ReAct tool calls 42.
- Vanilla RAG: 6/6 passed; mean judge 1.00; median latency 12422 ms; ReAct tool calls 0.
````

## Failure analysis and pivots

This section is a required project log. Record attempted approaches, observed failures with reproducible evidence, and the change made in response. Do not invent outcomes before experiments are run.

| Date / experiment | Attempt | Observed failure or limitation | Pivot / follow-up |
|---|---|---|---|
| Planning | Inspected the supplied project workspace | No existing source repository or project files were present | Establish the design and plan before scaffolding implementation |
| 2026-09-24, baseline integration | Wired the upstream Markdown renderer to an in-memory writer and loaded fixture paths from classpath resources | The renderer closes its writer, and URL-encoded resource paths are not valid Windows filesystem paths | Use the library's expected writer type and resolve fixtures from their resource URI; both baseline tests now pass |
| 2026-09-24, compatibility fixtures | Compared added required request field, removed response property, and renamed path parameter | The diff library marks the removed response property backward compatible and normalizes a parameter rename to no difference | Preserve the library result, add a separate review-required note for removed response properties, and independently surface renamed path parameters |
| 2026-09-24, consumer search fixture | Scanned the sample Maven/Spring Boot consumer for `getName` | Literal search finds method declarations and test method names as well as use sites | Return all matches with file/line evidence and document that a reviewer must distinguish usage from declarations |
| 2026-09-24, automatic symbol matching | Derived bean accessors for changed response/request properties and searched with identifier boundaries | A literal `getName` substring also matched a camel-case test method name unrelated to the getter call | Require identifier boundaries and label method declarations separately; retain manual review because this is still lexical, not AST-aware |
| 2026-09-24, lexical retrieval baseline | Ranked Java source/test chunks and OpenAPI diff evidence with a deterministic lexical score | Small corpora can produce ties and lexical overlap does not establish semantic relevance | Keep source citations and deterministic ordering; expand the labeled retrieval set and analyze false positives before choosing more complex retrieval |
| 2026-09-24, retrieval ranking check | Queried for the Java use of a removed widget name field | The first ranking put Maven configuration and diff text above code evidence | Added a fixed stop-word set and explicit kind weights that favor Java source/test evidence; the top-three check now includes the consumer source and its test |
| 2026-09-25, first ReAct smoke run | Ran the sample removed-property case with `--react` | The agent found the getter, service call site, and test, but three of eight tool calls were rejected for invalid read ranges; total latency was 33.2 seconds and the call cap was reached | Simplified file reading to a repository-relative Java path plus optional start line, with an automatic 80-line maximum; rerun the same case before evaluating tool efficiency |
| 2026-09-25, ReAct retry smoke run | Re-ran the same case after simplifying the read tool | The deterministic diff completed, but the first model request failed with HTTP 503 before any tool trace was produced; OpenAI client retries were configured as zero | Raised the bounded client retry allowance to two; rerun to determine whether the provider error was transient. No ReAct tool behavior was observed in this run |
| 2026-09-25, successful ReAct smoke rerun | Re-ran the same sample case after the read-tool simplification and retry configuration change | The answer cited the removed property and three relevant Java files; all seven tool calls returned observations with no invalid read ranges; latency was 32.1 seconds | Record as a single smoke observation, then compare Vanilla RAG and ReAct on a shared, labeled case set; this is not enough evidence to choose an architecture |
| 2026-09-27, first Promptfoo shared-case smoke | Ran Vanilla RAG and ReAct against the same removed-response-property case with the shared symbol check and LLM judge | Both providers passed both assertions and received judge score 1.0; the run took 67 seconds | Treat as harness validation only; expand the labeled set before comparative claims |
| 2026-09-27, five-case Promptfoo development run | Ran both approaches over removed/added operations, a newly required request field, a removed response field, and a renamed path parameter | All 10 provider-case runs passed the deterministic phrase check and judge rubric; every judge score was 1.0. Median model latency was 24.1 s for Vanilla RAG and 34.0 s for ReAct; both ranged up to 110.7 s. The output also showed a telemetry shutdown timeout after evaluation completed | Record as a successful development-set run only. Review the long-latency cases and add a separate held-out set before deciding between approaches |
| 2026-09-28, six-case Promptfoo development run | Ran both approaches once on the shared six-case set, including Stripe Basil-to-Clover, with the symbol assertion and LLM-as-a-judge rubric | Vanilla RAG passed 6/6 (mean judge 1.00; median latency 14.8 s). ReAct passed 5/6 (mean judge 0.92; median latency 29.5 s). The sole ReAct failure was the newly required `category` request property: it identified the contract change but asserted the sample service/tests needed edits without evidence those files send that request. Promptfoo completed and saved the results, then returned exit code 100 because one assertion failed; the summary was generated from the saved result file. | Review and tighten ReAct grounding so it distinguishes confirmed API requirements from unverified repository impact. Repeat the comparison, then assess on held-out cases before choosing an architecture. This is one run and not a final ranking |
| 2026-09-28, ReAct grounding prompt revision | Updated the ReAct system instructions after the unsupported-impact finding | No follow-up model run has been performed yet, so improvement is unverified | Rerun the same shared set and inspect whether the agent treats model/service/test mentions as insufficient proof of an API call or payload mapping |
| 2026-09-28, six-case rerun after grounding revision | Rebuilt, ran 26 Maven tests, checked out/searched the public Spring Petclinic repository, then ran both approaches on all six shared cases | All 12 approach-case results passed with mean judge scores of 1.00. Median latency: Vanilla RAG 12.4 s, ReAct 33.5 s. ReAct made 42 tool calls total. The previously failing newly-required-field ReAct case passed after the instruction change. | The revised instruction addresses the observed failure in this rerun, but one six-case run does not establish general improvement or an architecture winner. Preserve this run, repeat on additional/held-out cases, and review answer grounding manually |

## Evaluation

The initial API fixture inventory is in `evaluation/cases/baseline-cases.json`; repository-search fixtures are in `src/test/resources/consumer-project`; retrieval expectations and shared Promptfoo cases are in `evaluation/retrieval-cases.json` and `evaluation/promptfoo/config.yaml`. In the first six-case development run, Vanilla RAG passed 6/6 and ReAct 5/6; the ReAct failure was an unsupported repository-impact claim in the newly required request-field case. After tightening the ReAct grounding instruction, the six-case rerun passed 6/6 for both approaches, with mean judge scores of 1.00. Median latency was 12.4 seconds for Vanilla RAG and 33.5 seconds for ReAct; ReAct made 42 tool calls. The previous failing case passed in the rerun. Raw output and the aggregate summary at `evaluation/promptfoo/results.json` and `evaluation/promptfoo/summary.json` reflect this latest run. These are development cases and one rerun; they do not establish an architecture winner. Repeat and assess on a separate held-out set, with human review of answer grounding.

### Initial retrieval measurement

On the single development case in `evaluation/retrieval-cases.json`, top-3 returned both labeled source files (consumer service and test): **Recall@3 = 2/2 (100%)**. One of the three retrieved chunks was an irrelevant DTO definition, so **Precision@3 = 2/3 (66.7%)**. This is an initial fixture check, not a general performance claim; the case set needs to grow before drawing conclusions.
