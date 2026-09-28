# API Dependabot: project plan

## Goal and capstone fit

Build a narrow assistant that compares versions of OpenAPI 3.x contracts, finds likely impact in Java/Spring Boot consumer repositories, and helps prepare a migration that can be reviewed through GitHub. The project demonstrates an actively queried repository/specification surface, retrieval and orchestration, a controlled comparison of Vanilla RAG with a ReAct tool-using agent, and evidence-based architecture choice. It does not claim in advance that the agent will perform better.

## First milestone: deterministic change baseline

Before model work, build a repeatable input/output path for a pair of OpenAPI 3.x documents. It will validate and normalize the specs, identify endpoint and schema changes, classify known breaking changes with explicit rules, and emit a structured report with source paths and reasons. Add a small, hand-curated evaluation set with expected changes and outcomes. This gives us a trustworthy reference for later impact analysis and prevents the LLM from becoming the source of truth for contract diffs.

**Exit criteria:** reproducible reports for the initial cases; clear, documented limits for the breaking-change rules; and a repository layout/build that can support the later Java consumer and evaluation harness. The first cases and expected answers must be authored from real, inspectable fixtures, not fabricated as evaluation results.

## Build sequence

1. **Inspect and plan** — inventory available files and specification; capture scope, design, decisions, and open risks. (Complete: no pre-existing source repository or project files were present.)
2. **Deterministic OpenAPI baseline** — Java 21, Spring Boot, Maven; parse OpenAPI 3.x and produce version diffs. Start with a CLI/service boundary and fixture-based tests. (Baseline covers added/removed operations, newly required request fields, removed request/response fields including nested arrays, and renamed path parameters. Removed-field summaries now flag possible consumer breaks.)
3. **Java consumer impact** — initial slice complete: derive likely search terms from added/removed operations, required request fields, removed response fields, and renamed path parameters; scan a bounded Maven/Spring Boot repository and return source/test paths, lines, snippets, and likely declaration/reference labels. Matching uses identifier boundaries but remains lexical, not AST-based.
4. **Retrieval baseline** — initial lexical baseline complete: index bounded source/test/config chunks plus contract diff evidence; return BM25-style ranked chunks with source line ranges. Evaluate recall against hand-labeled cases before adding embeddings or vector storage.
5. **Vanilla RAG** — initial single-pass answer path implemented: retrieve once, pass bounded diff/evidence to Spring AI, and return a cited recommendation, evidence map, and latency. The OpenAI model call is opt-in; offline mode is the default. Patch generation remains future work.
6. **ReAct agent** — initial read-only tool loop implemented with Spring AI `ChatClient`: inspect diff, search Java, read bounded Java ranges, and retrieve evidence. Tool calls are traced and scoped to the supplied local checkout, with an eight-call cap. `--repo` now accepts a GitHub HTTPS URL and clones a shallow snapshot; `GITHUB_TOKEN` supports private repositories. The public-repository clone/search smoke check passed. Docker test execution and a GitHub PR review workflow remain future steps; do not auto-merge or deploy.
7. **Comparative evaluation** — use Promptfoo as the evaluation runner with custom providers invoking both CLI modes. The six-case shared set covers the original five cases plus Stripe Basil→Clover consumer impact. The first 2026-09-28 run passed 6/6 for Vanilla RAG and 5/6 for ReAct; ReAct made an unsupported claim about repository changes for a new request property. After tightening the grounding instructions, the rerun passed 6/6 for both approaches with mean judge scores of 1.00. Median latencies were 12.4 s (Vanilla RAG) and 33.5 s (ReAct); ReAct made 42 tool calls total. The earlier failing case passed. These remain development-set results from one rerun, not a final ranking. Continue with held-out cases and human review. The validation script saves raw Promptfoo answers and a summary of pass rate, judge score, latency, and ReAct tool use.
8. **Submission packaging** — complete: README includes failure analysis/pivots and one-command evaluation instructions; the one-page summary, 2-page design PDF, 5-page project documentation PDF, Markdown sources, and timed 3-minute demo script are in `submission/` and `DESIGN.md`. The clean deliverable copy is named `API Dependabot`; the original workspace remains temporarily because Windows reports it is in use. This workspace has no Git remote; repository review access still needs a hosting URL from the owner.

## Evaluation discipline

The evaluation set should cover additive and breaking contract changes, required request fields, removed/renamed response fields, endpoint changes, ambiguous changes, and irrelevant repository matches. Every case records source fixtures, expected change classification, affected code locations (when applicable), and a reference migration assessment. Keep development and held-out cases separate. Report exact denominators, failed/abstained runs, and model/config versions. Do not present judge scores as ground truth; pair them with deterministic checks and human review of a sample.

## Stack and scope controls

- Java 21, Spring Boot, Maven, Spring AI for model integration once needed.
- OpenAPI parser/diff libraries behind a small adapter; validate library behavior against explicit fixtures before relying on classifications.
- GitHub API for repository access and a reviewable PR workflow, introduced after local repository analysis works.
- Docker for isolated Maven test execution when that stage is reached.
- PostgreSQL only if persistence or repeatable corpus/evaluation management needs justify it; no vector database in the initial baseline.
- MVP is limited to OpenAPI 3.x, Java/Spring Boot, Maven, and GitHub. No other languages/protocols, auto-merge, production deploy, or multi-agent system.

## Risks and decisions to revisit

- OpenAPI diffs do not establish which changes are breaking for every client; keep rules explicit and support an "uncertain" classification.
- Java references can be indirect, generated, or reflection-based; report evidence and confidence rather than asserting complete impact coverage.
- Generated SDKs and provider-specific semantics may fall outside the first fixtures; track these as failure cases.
- LLM recommendations can be plausible but wrong; constrain edits, preserve citations, run tests in isolation, and require human review.
- Revisit retrieval strategy, persistence, and framework choices only after the baseline and evaluation show a concrete need.

## Current status

Planning and architecture artifacts are established. Root Maven verification passed **26 tests** with no failures using the optional `ecj-compiler` profile; the complete validation run also passed the Spring Petclinic checkout/search smoke check. Following the ReAct grounding revision, the six-case Promptfoo rerun passed 6/6 for both Vanilla RAG and ReAct; results and failure history are documented in the README and report. No architecture choice is supported yet. Remaining evaluation work is held-out cases and human review. Docker-isolated consumer test execution, HTTP hosting, and GitHub PR creation are outside the submitted prototype. Submission documents and demo script are packaged. The local folder is not connected to a Git remote, so review access must be configured by the project owner.
