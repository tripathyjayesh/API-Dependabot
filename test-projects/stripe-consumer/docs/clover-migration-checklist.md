# Clover migration checklist for the Basil consumer

Use this after Dependabot has reported the Basil-to-Clover diff:

- Schedule requests: replace `SchedulePhase.iterations` with a `duration` object (`interval` and `interval_count`).
- Promotion code requests: replace `coupon` with `promotion` containing `type="coupon"` and `coupon`.
- Discount responses: stop reading the auto-expanded `coupon`; read `source` and fetch/expand coupon details explicitly when needed.
- Checkout Session responses: keep using the existing `currency` field and remove reads of `currency_conversion`.
- Subscription creation: set the desired `billing_mode.type` explicitly. Set `classic` when the consumer relies on Basil-era classic behavior; otherwise adopt flexible and review resulting invoice/subscription behavior.

The five unaffected control routes remain unchanged. Re-run the controller tests and the Dependabot comparison after updating the DTOs and accessors. This fixture does not create Stripe transactions or call Stripe servers.
