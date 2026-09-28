# API Dependabot Design Document

**Prepared for the AI Engineering Capstone | 28 September 2026**

## Problem statement

Java and Spring Boot teams that consume third-party APIs need to understand whether a new API contract changes their client code, where that code lives, and what they should review before upgrading. API Dependabot compares two OpenAPI 3.x specifications, searches a Java/Maven consumer repository for related evidence, and provides an evidence-cited migration assessment. The capstone prototype is deliberately limited to OpenAPI 3.x, Java/Spring Boot, Maven, and GitHub repositories.

The tool is an advisory command-line prototype. It does not autonomously edit code, run the consumer's tests in Docker, open a GitHub pull request, or deploy a service. Contract facts come from deterministic comparison; model recommendations are qualified by the repository evidence found.

## Architecture and core flow

```text
Old and new OpenAPI files
          |
          v
Deterministic parse and diff ------> Contract change report
                                           |
GitHub URL or local checkout             |
          |                                |
          v                                v
Java source/test search + lexical retrieval
          |                                |
          +-------- same case inputs ------+
                    /          \
                   v            v
             Vanilla RAG       ReAct
             one retrieval     bounded read-only tool loop
                   \            /
                    v          v
          Evidence-cited migration assessment
```

The parser/diff layer identifies contract changes and preserves the library's classification alongside explicit review notes for known ambiguous cases. The repository surface supports a local checkout or a shallow GitHub clone. Java search reports file and line evidence; lexical retrieval ranks 25-line chunks with 5-line overlap across Java source, tests, build metadata, README, and the current diff. ReAct tools can inspect the diff, search Java, read bounded Java ranges, and retrieve ranked evidence. They are read-only, repository-scoped, and limited to eight calls per answer.

Vanilla RAG retrieves once and sends the bounded evidence with the diff to Spring AI for a single answer. ReAct can request observations from the tools before it answers. Both approaches use the same six labeled Promptfoo cases and the same answer rubric. OpenAI model access is opt-in; offline diff and repository analysis do not require a model key.

## Evaluation criteria

The current harness measures case pass rate, LLM-judge score, median latency, and ReAct tool calls. A deterministic symbol assertion checks for a case-specific key term; the judge assesses correctness, evidence, and uncertainty. Six development cases cover added/removed operations, request/response fields, a renamed path parameter, and a Stripe Basil-to-Clover migration. The latest rerun passed all 12 approach-case executions. There is not yet a held-out set, repeated sampling, or human adjudication at scale, so the run does not establish a final architecture winner.

The next evaluation stage is a held-out case set and human review of a sample. Compare answer correctness and grounding with latency and tool use; retain failures and denominators. Do not treat the LLM judge as ground truth.

## Framework choices and boundaries

Java 21, Spring Boot, and Maven fit the consumer ecosystem and the developer's Java experience. Spring AI supplies the chat and tool-calling integration while keeping deterministic diff and retrieval code separately inspectable. OpenAPI parser/diff tooling avoids hand-written specification parsing, with curated fixtures guarding against library classification gaps. Promptfoo runs the paired evaluation. A vector database and PostgreSQL were not added because the current small, local corpus does not justify them.

The prototype can miss generated, reflective, indirect, or non-Java call paths. A model/service/test mentioning a field is not sufficient proof that it sends an affected request; recommendations must distinguish contract requirements from verified repository impact. GitHub support currently reads a shallow snapshot and does not publish changes. Production hosting, authentication, Docker-isolated test execution, PR creation, and deployment are outside the submitted implementation.
