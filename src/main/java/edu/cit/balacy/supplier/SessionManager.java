package edu.cit.balacy.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

/** Signs in when needed, and signs in again when the client says the session was rejected. */
@Component
class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private static final String LOGIN_PATH = "/auth/token";

    private final RestClient rest;
    private final XmlTranslator xml;
    private final String clientId;
    private final String apiKey;
    private final long ttlSeconds;
    private final String instanceId;

    private String token;
    private Instant issuedAt;

    SessionManager(@Qualifier("legacySupplyRestClient") RestClient legacySupplyRestClient,
                   XmlTranslator xml,
                   @Value("${supplier.client-id:}") String clientId,
                   @Value("${supplier.api-key:}") String apiKey,
                   @Value("${supplier.session-ttl-seconds:0}") long ttlSeconds,
                   @Qualifier("clientInstanceId") String instanceId) {
        this.rest = legacySupplyRestClient;
        this.xml = xml;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.ttlSeconds = ttlSeconds;
        this.instanceId = instanceId;
    }

    synchronized String current() {
        // Optional proactive refresh once you have MEASURED the session lifetime.
        if (token != null && ttlSeconds > 0
                && Instant.now().isAfter(issuedAt.plus(Duration.ofSeconds(ttlSeconds)))) {
            token = null;
        }
        if (token == null) {
            token = login();
            issuedAt = Instant.now();
        }
        return token;
    }

    /** Drops the session only if it is still the one that was rejected. */
    synchronized void invalidate(String rejectedToken) {
        if (rejectedToken == null || rejectedToken.equals(token)) {
            token = null;
        }
    }

    private String login() {
        if (apiKey.isBlank() || clientId.isBlank()) {
            throw new IllegalStateException("LS_CLIENT_ID / LS_API_KEY environment variables are not set");
        }
        log.info("Signing in to LegacySupply");
        String response = rest.post().uri(LOGIN_PATH)
                .header("X-Client-Instance", instanceId)
                .contentType(MediaType.APPLICATION_XML)
                .accept(MediaType.APPLICATION_XML)
                .body(xml.loginXml(clientId, apiKey))
                .retrieve()
                .body(String.class);
        return xml.parseSessionToken(response);
    }
}
