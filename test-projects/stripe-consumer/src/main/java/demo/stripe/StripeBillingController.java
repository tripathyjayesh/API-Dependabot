package demo.stripe;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** Local fixtures shaped around Stripe API resources; no Stripe credentials or network calls. */
@RestController
@RequestMapping("/consumer/stripe")
public class StripeBillingController {
    // Affected 1: Clover replaces phases[].iterations with phases[].duration.
    @PostMapping("/subscription-schedules")
    public Map<String, Object> createSchedule(@RequestBody ScheduleRequest request) {
        int totalIterations = request.phases().stream().mapToInt(SchedulePhase::iterations).sum();
        return Map.of("id", "sub_sched_demo", "total_iterations", totalIterations);
    }

    // Affected 2: Clover replaces PromotionCode.coupon with promotion.type/coupon.
    @PostMapping("/promotion-codes")
    public Map<String, Object> createPromotionCode(@RequestBody PromotionCodeRequest request) {
        return Map.of("id", "promo_demo", "coupon", request.coupon());
    }

    // Affected 3: Basil consumer expects an expanded Discount.coupon response.
    @GetMapping("/subscriptions/{id}")
    public SubscriptionDiscountResponse getSubscription(@PathVariable String id) {
        return new SubscriptionDiscountResponse(id,
                List.of(new DiscountResponse("di_demo", new Coupon("coupon_demo", "Spring offer"))));
    }

    // Affected 4: Clover removes Checkout Session.currency_conversion.
    @GetMapping("/checkout-sessions/{id}")
    public CheckoutSessionResponse getCheckoutSession(@PathVariable String id) {
        return new CheckoutSessionResponse(id, "usd", Map.of("amount", 1200, "source_currency", "eur"));
    }

    // Affected 5: Clover's flexible billing default changes subscription behavior.
    @PostMapping("/subscriptions")
    public Map<String, Object> createSubscription(@RequestBody SubscriptionRequest request) {
        return Map.of("id", "sub_demo", "customer", request.customer(),
                "billing_mode", "classic (assumed by this Basil-era consumer)");
    }

    public record ScheduleRequest(String customer, List<SchedulePhase> phases) {}
    public record SchedulePhase(String price, int iterations) {}
    public record PromotionCodeRequest(String coupon) {}
    public record Promotion(String type, String coupon) {}
    public record Coupon(String id, String name) {}
    public record SubscriptionDiscountResponse(String id, List<DiscountResponse> discounts) {}
    public record DiscountResponse(String id, Coupon coupon) {}
    public record CheckoutSessionResponse(String id, String currency,
                                          @JsonProperty("currency_conversion") Map<String, Object> currencyConversion) {}
    public record SubscriptionRequest(String customer, String price) {}
}
