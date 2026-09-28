# Stripe migration notes (local knowledge base)

Target API version: `2025-09-30.clover`. Baseline: `2025-08-27.basil`.

For Clover, change subscription schedule `phases[].iterations` to `phases[].duration`. For promotion code creation, replace the top-level `coupon` parameter with `promotion.type=coupon` and `promotion.coupon`. Discount responses no longer auto-expand `coupon`; use the new `source` property and retrieve/expand its coupon as needed. Checkout Session responses no longer contain `currency_conversion`; the existing `currency` property remains. New subscriptions default to flexible billing; integrations that need classic behavior should explicitly pass `billing_mode.type=classic`.

Control endpoints in this test: retrieve Customer, Product, Coupon, File, and Price. The selected changelog entries do not change these operations.

Stripe docs: https://docs.stripe.com/changelog/clover/2025-09-30/polymorphic-coupon ; https://docs.stripe.com/changelog/clover/2025-09-30/add-discount-source-property ; https://docs.stripe.com/changelog/clover/2025-09-30/itemize-proration-discount-amounts
