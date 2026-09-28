package demo.stripe;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Five control endpoints left unchanged by the selected Clover changelog entries. */
@RestController
@RequestMapping("/consumer/stripe")
public class StripeCatalogController {
    @GetMapping("/customers/{id}")
    public Map<String, Object> customer(@PathVariable String id) { return Map.of("id", id, "object", "customer"); }

    @GetMapping("/products/{id}")
    public Map<String, Object> product(@PathVariable String id) { return Map.of("id", id, "object", "product"); }

    @GetMapping("/coupons/{id}")
    public Map<String, Object> coupon(@PathVariable String id) { return Map.of("id", id, "object", "coupon"); }

    @GetMapping("/files/{id}")
    public Map<String, Object> file(@PathVariable String id) { return Map.of("id", id, "object", "file"); }

    @GetMapping("/prices/{id}")
    public Map<String, Object> price(@PathVariable String id) { return Map.of("id", id, "object", "price"); }
}
