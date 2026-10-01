package edu.cit.balacy.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * HTTP client for LegacySupply: timeouts (see SupplierConfig), max 3 attempts
 * with backoff, transparent re-login. The X-Request-Id is supplied by the
 * caller (it is stored in the DB) so it is identical on every attempt.
 */
@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);

    private static final String PO_PATH = "/purchase-orders";
    private static final String CATALOG_PATH = "/catalog";
    private static final String SESSION_HEADER = "X-LS-Session";
    private static final int MAX_ATTEMPTS = 3;

    record Ack(String poNumber, String statusCode) {
    }

    record CatalogItem(String sku, String description, int packSize) {
    }

    private final RestClient rest;
    private final SessionManager sessions;
    private final XmlTranslator xml;
    private final String instanceId;

    LegacySupplyClient(@Qualifier("legacySupplyRestClient") RestClient legacySupplyRestClient,
                       SessionManager sessions, XmlTranslator xml,
                       @Qualifier("clientInstanceId") String instanceId) {
        this.rest = legacySupplyRestClient;
        this.sessions = sessions;
        this.xml = xml;
        this.instanceId = instanceId;
    }

    Ack createPurchaseOrder(String requestId, String buyerRef, String sku, int cases) {
        String body = xml.purchaseOrderXml(buyerRef, sku, cases);
        String response = execute(token -> rest.post().uri(PO_PATH)
                .header(SESSION_HEADER, token)
                .header("X-Client-Instance", instanceId)
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_XML)
                .accept(MediaType.APPLICATION_XML)
                .body(body)
                .retrieve()
                .body(String.class));
        return xml.parseAck(response);
    }

    /** Raw LegacySupply status code for a PO. Translation happens in XmlTranslator. */
    String fetchStatusCode(String poNumber) {
        String response = execute(token -> rest.get().uri(PO_PATH + "/{po}", poNumber)
                .header(SESSION_HEADER, token)
                .header("X-Client-Instance", instanceId)
                .accept(MediaType.APPLICATION_XML)
                .retrieve()
                .body(String.class));
        return xml.parseStatusCode(response);
    }

    /** Looks for an order already placed under our BuyerRef (duplicate protection). */
    Optional<Ack> findByBuyerRef(String buyerRef) {
        String response = execute(token -> rest.get().uri(PO_PATH + "?buyerRef={ref}", buyerRef)
                .header(SESSION_HEADER, token)
                .header("X-Client-Instance", instanceId)
                .accept(MediaType.APPLICATION_XML)
                .retrieve()
                .body(String.class));
        return xml.parseFirstOrder(response);
    }

    List<CatalogItem> fetchCatalog() {
        String response = execute(token -> rest.get().uri(CATALOG_PATH)
                .header(SESSION_HEADER, token)
                .header("X-Client-Instance", instanceId)
                .accept(MediaType.APPLICATION_XML)
                .retrieve()
                .body(String.class));
        return xml.parseCatalog(response);
    }

    private String execute(Function<String, String> call) {
        boolean reloggedIn = false;
        for (int attempt = 1; ; attempt++) {
            String token = null;
            try {
                token = sessions.current();
                return call.apply(token);

            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                String errCode = xml.parseErrorCode(e.getResponseBodyAsString());

                if (status == 401) {
                    if ("E-AUTH-01".equals(errCode)) {
                        // Bad Client ID / API key: a configuration problem, not an order problem.
                        log.error("LegacySupply rejected our credentials (E-AUTH-01). Check LS_CLIENT_ID / LS_API_KEY.");
                        throw new SupplierUnavailableException("Credentials rejected (E-AUTH-01)", e);
                    }
                    // E-AUTH-03 / E-AUTH-07 (and E-AUTH-02): the session is not accepted -> sign in again.
                    sessions.invalidate(token);
                    if (!reloggedIn) {
                        log.info("Session rejected ({}), signing in again", errCode);
                        reloggedIn = true;
                        attempt--;              // re-login does not consume a retry
                        continue;
                    }
                    throw new SupplierUnavailableException("Session still rejected after re-login (" + errCode + ")", e);
                }
                if (status == 429 || status >= 500) {   // E-RATE-03, E-SYS-50, E-SYS-99
                    if (attempt >= MAX_ATTEMPTS) {
                        throw new SupplierUnavailableException("Supplier returned " + status + " " + errCode, e);
                    }
                    log.warn("LegacySupply returned {} {} (attempt {}/{})", status, errCode, attempt, MAX_ATTEMPTS);
                    backoff(attempt, status == 429);
                    continue;
                }
                // 400 / 404 / 409 / 415 / 422: retrying the same request will not help.
                throw new SupplierRejectedException("Supplier rejected request: HTTP " + status + " " + errCode);

            } catch (ResourceAccessException e) {   // timeout, refused, DNS, reset
                if (attempt >= MAX_ATTEMPTS) {
                    throw new SupplierUnavailableException("Supplier unreachable", e);
                }
                log.warn("LegacySupply unreachable (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                backoff(attempt, false);
            }
        }
    }

    private void backoff(int attempt, boolean rateLimited) {
        long base = rateLimited ? 2000L : 500L;
        long delay = base * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(200);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new SupplierUnavailableException("Interrupted during backoff", ie);
        }
    }
}
