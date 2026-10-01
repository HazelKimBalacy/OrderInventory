package edu.cit.balacy.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
class MarketplaceApiClient {

    private static final int MAX_ATTEMPTS = 3;

    private final RestClient rest;
    private final String clientId;
    private final String apiKey;
    private final String instanceId;

    MarketplaceApiClient(
            @Qualifier("tianggeRestClient") RestClient rest,
            @Value("${channel.client-id:}") String clientId,
            @Value("${channel.api-key:}") String apiKey,
            @Qualifier("clientInstanceId") String instanceId) {
        this.rest = rest;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instanceId = instanceId;
    }

    boolean isConfigured() {
        return !clientId.isBlank() && !apiKey.isBlank();
    }

    void heartbeat(String appName, Instant startedAt, long uptimeSeconds) {
        request(() -> authorizedRest().post().uri("/instances/heartbeat")
                .body(Map.of(
                        "appName", appName,
                        "startedAt", startedAt.toString(),
                        "uptimeSeconds", uptimeSeconds))
                .retrieve().toBodilessEntity());
    }

    void publishListings(List<Map<String, String>> listings) {
        if (!listings.isEmpty()) {
            request(() -> authorizedRest().put().uri("/listings")
                    .body(listings)
                    .retrieve().toBodilessEntity());
        }
    }

    void publishStock(List<Map<String, Object>> stock) {
        if (!stock.isEmpty()) {
            request(() -> authorizedRest().put().uri("/stock")
                    .body(stock)
                    .retrieve().toBodilessEntity());
        }
    }

    FeedPage fetchFeed(long after, int limit) {
        return request(() -> authorizedRest().get().uri(uriBuilder -> uriBuilder
                        .path("/feed")
                        .queryParam("after", after)
                        .queryParam("limit", limit)
                        .build())
                .retrieve().body(FeedPage.class));
    }

    void sendDecision(String orderId, MarketplaceDecision decision, Long shopOrderId, String reason) {
        if (shopOrderId == null) {
            throw new IllegalStateException("Cannot send a Tiangge decision without a local order ID");
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("decision", decision.name());
        body.put("shopOrderId", "SO-" + shopOrderId);
        if (reason != null && !reason.isBlank()) {
            body.put("reason", reason.length() <= 200 ? reason : reason.substring(0, 200));
        }
        request(() -> authorizedRest().post().uri("/orders/{orderId}/decision", orderId)
                .body(body)
                .retrieve().toBodilessEntity());
    }

    void confirmCancellation(String orderId) {
        request(() -> authorizedRest().post().uri("/orders/{orderId}/cancellation", orderId)
                .body(Map.of("restocked", true))
                .retrieve().toBodilessEntity());
    }

    void resolveBackorder(String orderId, String status) {
        request(() -> authorizedRest().post().uri("/orders/{orderId}/resolution", orderId)
                .body(Map.of("status", status))
                .retrieve().toBodilessEntity());
    }

    private <T> T request(Supplier<T> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if ((status == 408 || status == 429 || status >= 500) && attempt < MAX_ATTEMPTS) {
                    pause(attempt, status == 429);
                    continue;
                }
                throw e;
            } catch (ResourceAccessException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                pause(attempt, false);
            }
        }
    }

    private RestClient authorizedRest() {
        return rest.mutate()
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("X-Client-Instance", instanceId)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private void pause(int attempt, boolean rateLimited) {
        long base = rateLimited ? 1500L : 400L;
        try {
            Thread.sleep(base * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Tiangge request", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedPage(List<FeedEvent> events, long nextCursor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedEvent(long seq, String eventId, String type, String orderId, List<FeedLine> lines) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedLine(String sellerSku, int qty) {
    }
}
