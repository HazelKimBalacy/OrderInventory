package edu.cit.balacy.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
class ChannelListingCatalog {

    private static final Logger log = LoggerFactory.getLogger(ChannelListingCatalog.class);
    private final Map<String, String> supplierSkus = new LinkedHashMap<>();

    ChannelListingCatalog(@Value("${supplier.catalog:}") String configuredCatalog) {
        for (String entry : configuredCatalog.split(",")) {
            String[] fields = entry.trim().split(":");
            if (fields.length == 3 && !fields[0].isBlank() && !fields[1].isBlank()) {
                supplierSkus.put(fields[0].trim(), fields[1].trim());
            } else if (!entry.isBlank()) {
                log.error("Ignoring invalid supplier mapping '{}' in SUPPLIER_CATALOG", entry.trim());
            }
        }
    }

    Map<String, String> supplierSkus() {
        return Map.copyOf(supplierSkus);
    }
}
