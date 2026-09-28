package demo.stripe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StripeBillingControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void fiveUnaffectedControlEndpointsStillWork() throws Exception {
        mvc.perform(get("/consumer/stripe/customers/cus_demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.object").value("customer"));
        mvc.perform(get("/consumer/stripe/products/prod_demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.object").value("product"));
        mvc.perform(get("/consumer/stripe/coupons/coupon_demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.object").value("coupon"));
        mvc.perform(get("/consumer/stripe/files/file_demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.object").value("file"));
        mvc.perform(get("/consumer/stripe/prices/price_demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.object").value("price"));
    }

    @Test
    void basilScheduleRequestStillUsesIterations() throws Exception {
        mvc.perform(post("/consumer/stripe/subscription-schedules").contentType(APPLICATION_JSON)
                        .content("""
                                {"customer":"cus_demo","phases":[{"price":"price_demo","iterations":3}]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_iterations").value(3));
    }

    @Test
    void basilPromotionCodeRequestStillUsesTopLevelCoupon() throws Exception {
        mvc.perform(post("/consumer/stripe/promotion-codes").contentType(APPLICATION_JSON)
                        .content("{\"coupon\":\"coupon_demo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coupon").value("coupon_demo"));
    }

    @Test
    void basilConsumerShapeIncludesExpandedDiscountCoupon() throws Exception {
        mvc.perform(get("/consumer/stripe/subscriptions/sub_demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.discounts[0].coupon.name").value("Spring offer"));
    }

    @Test
    void basilCheckoutResponseIncludesCurrencyConversion() throws Exception {
        mvc.perform(get("/consumer/stripe/checkout-sessions/cs_demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency_conversion.amount").value(1200));
    }

    @Test
    void basilConsumerAssumesClassicSubscriptionBilling() throws Exception {
        mvc.perform(post("/consumer/stripe/subscriptions").contentType(APPLICATION_JSON)
                        .content("{\"customer\":\"cus_demo\",\"price\":\"price_demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing_mode").value("classic (assumed by this Basil-era consumer)"));
    }
}
