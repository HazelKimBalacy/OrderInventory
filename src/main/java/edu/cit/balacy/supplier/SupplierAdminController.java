package edu.cit.balacy.supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV/LAB helper: POST /api/supplier/reorder/P100?units=20
 * Sends a real reorder through the normal SupplierGateway (same idempotent
 * X-Request-Id, PENDING-on-outage, tracking). Lets you place the 3 purchase
 * orders without having to drain stock through the shop UI first.
 * Remove before production.
 */
@RestController
@RequestMapping("/api/supplier")
public class SupplierAdminController {

    private final SupplierGateway gateway;

    public SupplierAdminController(SupplierGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/reorder/{productId}")
    public ResponseEntity<SupplierOrderResult> reorder(@PathVariable String productId,
                                                       @RequestParam(defaultValue = "20") int units) {
        return ResponseEntity.ok(gateway.requestReorder(productId, units));
    }
}
