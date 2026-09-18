package dev.dhruv.jobsearch.connected;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ConnectedBrokerClient {
    private static final int MAX_BROKER_RESPONSE_BYTES = 4 * 1024 * 1024;
    private final boolean enabled;
    private final String baseUrl;
    private final String token;
    private final RestClient client;

    public ConnectedBrokerClient(@Value("${app.connected.enabled:false}") boolean enabled,
            @Value("${app.connected.broker-base-url:}") String baseUrl,
            @Value("${app.connected.broker-token:}") String token) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.token = token == null ? "" : token.trim();
        validateConfiguration();
        this.client = this.baseUrl.isBlank() ? null : RestClient.builder().baseUrl(this.baseUrl).build();
    }

    public boolean configured() { return enabled && client != null && token.length() >= 32; }

    public String postOpenAi(Object body) {
        requireConfigured();
        return client.post().uri("/openai/v1/responses").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token).body(body).exchange((request, response) -> {
                    byte[] responseBody = readBounded(response.getBody());
                    if (response.getStatusCode().value() < 200 || response.getStatusCode().value() >= 300) {
                        throw new IllegalStateException("The reviewed provider request was rejected.");
                    }
                    return new String(responseBody, StandardCharsets.UTF_8);
                });
    }

    public JobPageResponse fetchJobPage(String url) {
        requireConfigured();
        return client.post().uri("/v1/job-page").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token).body(java.util.Map.of("url", url))
                .exchange((request, response) -> {
                    byte[] body = readBounded(response.getBody());
                    if (response.getStatusCode().value() < 200 || response.getStatusCode().value() >= 300) {
                        throw new IllegalStateException("The reviewed egress broker rejected the job-page request.");
                    }
                    String encoded = response.getHeaders().getFirst("x-jscc-final-url");
                    if (encoded == null || encoded.isBlank()) {
                        throw new IllegalStateException("The reviewed egress broker omitted the validated final URL.");
                    }
                    String finalUrl = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
                    return new JobPageResponse(finalUrl, body);
                });
    }

    private void requireConfigured() {
        if (!configured()) throw new IllegalStateException(
                "The reviewed connected runtime is not active. Return to local-only or start the connected Compose profile.");
    }

    private void validateConfiguration() {
        if (!enabled) return;
        if (baseUrl.isBlank() || token.length() < 32) {
            throw new IllegalStateException("Connected runtime requires its internal broker URL and a 32+ character token.");
        }
        URI uri = URI.create(baseUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))) {
            throw new IllegalStateException("Connected broker URL must be a plain internal HTTP origin.");
        }
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean localTest = "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!"egress-broker".equals(host) && !localTest) {
            throw new IllegalStateException("Connected broker URL must target the reviewed egress-broker service.");
        }
        if ("egress-broker".equals(host) && uri.getPort() != 8787) {
            throw new IllegalStateException("Connected broker URL must use the reviewed internal broker port.");
        }
    }

    private byte[] readBounded(java.io.InputStream input) throws java.io.IOException {
        byte[] body = input.readNBytes(MAX_BROKER_RESPONSE_BYTES + 1);
        if (body.length > MAX_BROKER_RESPONSE_BYTES) {
            throw new IllegalStateException("The broker response exceeded the connected-runtime limit.");
        }
        return body;
    }

    public record JobPageResponse(String finalUrl, byte[] body) {}
}
