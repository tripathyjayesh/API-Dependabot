# Test observations

## User PowerShell run — 2026-09-28

The complete terminal capture is preserved at [`outputs/stripe-clover-dependabot-run-2026-09-28.txt`](../../../outputs/stripe-clover-dependabot-run-2026-09-28.txt). It contains the offline diff/retrieval run followed by the `-LiveAnswer` run.

Command sequence:

```powershell
./run-dependabot.ps1 -TargetVersion 2025-09-30.clover
./run-dependabot.ps1 -TargetVersion 2025-09-30.clover -LiveAnswer
```

Observed results:

- Both commands completed successfully from the user's PowerShell session. This supersedes the earlier Codex-sandbox-only `AccessDeniedException` note; source retrieval works in the user's environment.
- The contract diff reported schedule `iterations` removed and `duration` added; promotion-code top-level `coupon` replaced by `promotion`; subscription Discount `coupon` replaced by `source`; Checkout Session `currency_conversion` removed; and `billing_mode` added for subscription creation.
- In the archived run, despite those removed fields, the analyzer summary said `API changes are backward compatible`. Its extra consumer-impact section flagged only Checkout Session `currency_conversion`; it missed the removed request fields and nested Discount field. This finding motivated the analyzer fix below.
- Offline lexical retrieval returned the Java controller and the Stripe notes in `src/README.md`. The highest-ranked chunk was the fixture's general `README.md` instructions (score 5.336), ahead of relevant Java chunks (4.543 and 3.893); the release-notes chunk ranked lower (1.667). This points to a retrieval-ranking improvement opportunity.
- The live Vanilla RAG answer identified all five affected consumer call sites and cited the fixture code, tests, OpenAPI diff, and release notes. Its reported model-call latency was 24,602 ms. No model call was made in the first, offline run.

## Fixture correction after reviewing the output

The first captured run incorrectly reported Checkout Session `currency` as newly added because the Basil snapshot omitted it. Stripe's API reference already includes `currency` for Basil; the Clover breaking change removes `currency_conversion`. I corrected `openapi/basil.yaml` so both snapshots include `currency`, and updated the migration guidance to keep using `currency` while removing `currency_conversion`. The archived run is intentionally unchanged as the record of what was observed; the captured RAG recommendation to treat `currency` as new should not be followed. See the [Stripe Checkout Sessions API reference](https://docs.stripe.com/api/checkout/sessions?api-version=2025-08-27.basil) and the [Clover changelog](https://docs.stripe.com/changelog#2025-09-30.clover).

After correcting the Basil snapshot, I reran the spec-only diff. It now reports only `currency_conversion` removed for the Checkout Session response; it no longer reports `currency` as added. The analyzer still labels the full release diff backward compatible.

## Analyzer classification fix — 2026-09-28

I updated `OpenApiDiffService` to inspect request and response schemas recursively, including arrays and composed schemas. Removed request/response properties now produce migration-review notes with nested field paths (for example, `discounts[].coupon`). When any such removal exists, the top-level result is changed from the diff library's generic `API changes are backward compatible` label to `API changes may break backward compatibility for existing consumers`.

Verification against the corrected Basil and Clover fixtures completed successfully using the rebuilt runnable JAR. The report now identifies all four removed fields:

- `POST /subscription_schedules`: request `phases[].iterations` removed.
- `POST /promotion_codes`: request `coupon` removed.
- `GET /subscriptions/{id}`: response `discounts[].coupon` removed.
- `GET /checkout/sessions/{id}`: response `currency_conversion` removed.

The report now uses the potentially breaking summary. This is a deterministic contract-level warning; it does not assert every removal breaks every consumer. A fresh full JUnit run is still outstanding: Maven test compilation on this Windows/JDK 21.0.4 environment fails inside the compiler with `AccessDeniedException` while closing a dependency JAR in the local Maven cache. The main application packaged successfully with tests skipped, and the rebuilt JAR produced the expected fixture diff. Earlier, the separate Stripe Spring fixture tests passed 6/6.

## Comparative evaluation case added — 2026-09-28

Added `stripe-basil-clover-consumer-001` to the Promptfoo shared-case configuration. It asks both Vanilla RAG and ReAct the same migration question using the corrected Basil/Clover contracts and the Stripe consumer fixture. The reference assessment covers the removed request/response fields, retained Checkout Session `currency`, flexible billing default, and the need to distinguish contract evidence from actual repository use.

The initial Promptfoo run was not completed in the sandbox because it could not access the profile database; redirecting the config directory then exposed a sandbox startup error. The user subsequently ran the targeted evaluation successfully with Node 22.23.3. Results are saved in [`evaluation/promptfoo/stripe-results.json`](../../../evaluation/promptfoo/stripe-results.json).

### User-run comparison result

- Promptfoo completed the Stripe case for both providers: **2/2 provider-case results passed**, with no failures or errors.
- Both Vanilla RAG and ReAct passed the expected-symbol check and received **1.0** from the LLM-as-a-judge rubric.
- Both answers identified the request and response field changes and flexible billing behavior. The judge found both answers grounded and appropriately qualified.
- Vanilla RAG produced a migration checklist and cited the OpenAPI diff and repository evidence. Its app-call latency was about 27 seconds.
- ReAct added pinpoint controller and test references and a more explicit per-endpoint migration plan; its trace shows 7 repository-tool calls. Its app-call latency was about 34 seconds.
- This is one evaluation case. It shows both approaches handled this case well; it is not enough evidence to conclude that ReAct is generally better. Continue with the remaining shared cases and compare accuracy, grounding, and latency across the full set before choosing an approach.

## Controller test suite

The separate Spring Boot fixture test suite passed: **6 tests, 0 failures, 0 errors**. The tests cover all ten local routes and the Basil-era request/response assumptions. These are local mocks and do not call Stripe.
