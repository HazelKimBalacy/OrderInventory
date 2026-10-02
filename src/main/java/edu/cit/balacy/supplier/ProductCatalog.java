package edu.cit.balacy.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Our product id -> LegacySupply SupplierSku + PackSize. The ONLY place
 * supplier item numbers and pack sizes live.
 *
 * Configured in application.properties as
 *   supplier.catalog=P100:ABC-1234:12,P200:XYZ-0001:6
 * (productId:SupplierSku:PackSize, from GET /catalog). The pack size is then
 * kept in sync with the live catalog by refreshFromSupplier(), so a supplier
 * outage never stops a reorder from being computed and stored.
 */
@Component
class ProductCatalog {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalog.class);

    record Entry(String sku, int packSize) {
    }

    private final LegacySupplyClient client;
    private final Map<String, Entry> configured = new HashMap<>();
    private volatile Map<String, Integer> livePackSizes = Map.of();

    ProductCatalog(LegacySupplyClient client, @Value("${supplier.catalog:}") String raw) {
        this.client = client;
        for (String part : raw.split(",")) {
            String[] f = part.trim().split(":");
            if (f.length != 3) {
                continue;
            }
            try {
                int pack = Integer.parseInt(f[2].trim());
                if (pack < 1) {
                    throw new NumberFormatException("pack size must be >= 1");
                }
                configured.put(f[0].trim(), new Entry(f[1].trim(), pack));
            } catch (NumberFormatException e) {
                // A typo in config must not stop the whole app from starting.
                log.error("Ignoring invalid supplier.catalog entry '{}' (expected productId:SupplierSku:PackSize)", part.trim());
            }
        }
        if (configured.isEmpty()) {
            log.warn("supplier.catalog is empty: no product can be reordered until it is filled in");
        }
    }

    Entry lookup(String productId) {
        Entry cfg = configured.get(productId);
        if (cfg == null) {
            throw new SupplierRejectedException("No supplier mapping for product " + productId
                    + " (set supplier.catalog in application.properties)");
        }

        return new Entry(cfg.sku(), livePackSizes.getOrDefault(cfg.sku(), cfg.packSize()));
    }

    boolean supportsProduct(String productId) {
        return configured.containsKey(productId);
    }

    /** Reads GET /catalog and keeps pack sizes current. Called by a scheduled job. */
    void refreshFromSupplier() {
        List<LegacySupplyClient.CatalogItem> items = client.fetchCatalog();
        Map<String, Integer> live = new HashMap<>();
        items.forEach(item -> {
            live.put(item.sku(), item.packSize());
            log.info("LegacySupply catalog item: SupplierSku={}, Description='{}', PackSize={}",
                    item.sku(), item.description(), item.packSize());
        });
        livePackSizes = live;

        configured.forEach((productId, cfg) -> {
            Integer pack = live.get(cfg.sku());
            if (pack == null) {
                log.warn("Product {} maps to SKU {} which is not in the supplier catalog", productId, cfg.sku());
            } else if (pack != cfg.packSize()) {
                log.warn("Pack size drift for {}: configured {}, supplier says {} (using supplier's)",
                        productId, cfg.packSize(), pack);
            }
        });
        log.info("Supplier catalog read: {} items", items.size());
    }
}
