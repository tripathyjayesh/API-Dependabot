# Stripe release evidence for this fixture

## Version selection

The test compares Stripe stable API version `2025-08-27.basil` to `2025-09-30.clover`. The Basil stable section of Stripe's changelog lists non-breaking changes. Some breaking changes are listed in the separate `2025-08-27.preview` section; this fixture does not mislabel preview changes as Basil stable behavior. The selected breaking changes below are documented for Clover.

## Five Clover-affected operations represented in the contracts

1. `POST /v1/subscription_schedules`: `phases[].iterations` is removed; use `phases[].duration`.
2. `POST /v1/promotion_codes`: top-level `coupon` is removed; use `promotion.type=coupon` and `promotion.coupon`.
3. `GET /v1/subscriptions/{id}`: expanded Discount objects in the subscription response no longer auto-expand `coupon`; they expose `source`, and coupon details need separate retrieval or expansion through `source`.
4. `GET /v1/checkout/sessions/{id}`: `currency_conversion` is removed from the Checkout Session response.
5. `POST /v1/subscriptions`: newly created subscriptions default to flexible billing. Integrations depending on classic behavior should specify `billing_mode.type=classic` explicitly. This is a behavior/default change, represented in the contract description rather than as a removed field.

## Five control operations

`GET /v1/customers/{id}`, `GET /v1/products/{id}`, `GET /v1/coupons/{id}`, `GET /v1/files/{id}`, and `GET /v1/prices/{id}` are included unchanged in both snapshots. “Unchanged” here means unchanged by the selected Clover changelog entries captured for this test, not that Stripe guarantees these APIs never change.

## Official sources

- [Stripe changelog](https://docs.stripe.com/changelog)
- [Promotion Codes use polymorphic promotion](https://docs.stripe.com/changelog/clover/2025-09-30/polymorphic-coupon)
- [Discount source replaces auto-expanded coupon](https://docs.stripe.com/changelog/clover/2025-09-30/add-discount-source-property)
- [Proration discount itemization and response semantics](https://docs.stripe.com/changelog/clover/2025-09-30/itemize-proration-discount-amounts)
- [Stripe API upgrades](https://docs.stripe.com/upgrades)

The OpenAPI files in this directory are intentionally curated test excerpts, not full Stripe API specifications. Behavioral changes and explanatory notes are supplied as local documentation because OpenAPI structural diff alone cannot reliably infer behavioral impact.
