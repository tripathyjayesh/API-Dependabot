# API Dependabot three minute demo

## Before recording

- Build the project and confirm Java 21 is selected.
- Keep the sample specs and consumer project ready in the repository.
- Set `OPENAI_API_KEY` and `SPRING_AI_MODEL_CHAT=openai` if showing live answers.
- Use the saved Promptfoo summary for the comparison segment; do not rerun the six-case evaluation during the recording.

## Timed walkthrough

| Time | Screen and narration |
|---|---|
| 0:00-0:30 | Introduce API Dependabot: it compares OpenAPI 3.x versions and finds likely impact in Java/Spring Boot consumer code. Clarify that this is an advisory command-line capstone prototype, not an automatic PR service. |
| 0:30-1:00 | Run `java -jar target/api-dependabot-0.1.0-SNAPSHOT.jar --old=src/test/resources/specs/v1.yaml --new=src/test/resources/specs/v2-removed-response-property.yaml`. Point out the removed response property and the separate review note. |
| 1:00-1:35 | Run the same question with `--repo=src/test/resources/consumer-project --question="Where does the consumer read the removed widget name?" --top-k=3 --answer`. Show the Vanilla RAG evidence and source locations. |
| 1:35-2:10 | Repeat the command with `--react` instead of `--answer`. Show the tool trace and explain that tools are read-only and scoped to the supplied repository. |
| 2:10-2:45 | Open `evaluation/promptfoo/summary.json`. Compare both approaches on the same six cases. Mention the first ReAct grounding failure, the instruction revision, and the passing rerun. State that this development set does not determine a final winner. |
| 2:45-3:00 | Close with limitations: lexical search, small case set, no held-out evaluation, no code edits, Docker test execution, or pull request creation. The next research step is held-out evaluation and human review. |

## Commands to rehearse

Run from the project root. First show the offline deterministic diff:

```powershell
java -jar target/api-dependabot-0.1.0-SNAPSHOT.jar --old=src/test/resources/specs/v1.yaml --new=src/test/resources/specs/v2-removed-response-property.yaml
```

Then set up the shared inputs and run the same question through each approach:

```powershell
$common = @(
  '-jar', 'target/api-dependabot-0.1.0-SNAPSHOT.jar',
  '--old=src/test/resources/specs/v1.yaml',
  '--new=src/test/resources/specs/v2-removed-response-property.yaml',
  '--repo=src/test/resources/consumer-project',
  '--question=Where does the consumer read the removed widget name?',
  '--top-k=3'
)
java @common --answer
java @common --react
```

The two answer commands make live model calls. Rehearse them before recording and use the saved outputs if you need to keep the recorded demo within three minutes.

## Recording notes

Use the deterministic diff for the first demonstration segment. Live answer commands incur model calls, so rehearse with the saved output and make only the two demonstration calls during the recording. Keep the evaluation summary visible for exact denominators and latency values.
