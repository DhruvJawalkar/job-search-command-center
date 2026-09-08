package dev.dhruv.jobsearch.skill;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LiveJobPageFetcher {

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;
    private static final int MIN_DESCRIPTION_CHARACTERS = 300;
    private static final String DESCRIPTION_SELECTORS = String.join(", ",
            "[data-automation-id=jobPostingDescription]", "[data-testid=job-description]",
            "[data-testid=jobDescription]", "#job-description", "#jobDescriptionText",
            ".job-description", ".jobDescription", ".posting-description", ".description__text",
            ".show-more-less-html", ".posting-page .content", "[class*=job-description]",
            "[class*=jobDescription]");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LiveJobPageFetcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public FetchedDescription fetch(String sourceUrl) {
        URI current = validatedUri(sourceUrl);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(18))
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("User-Agent", "JobSearchCommandCenter/1.0 (+local evidence capture)")
                    .GET().build();
            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException exception) {
                throw new IllegalStateException("The live job page could not be reached.", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("The live job-page request was interrupted.", exception);
            }
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                close(response.body());
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalStateException("The live job page returned an invalid redirect."));
                current = validatedUri(current.resolve(location).toString());
                continue;
            }
            if (status < 200 || status >= 300) {
                close(response.body());
                throw new IllegalStateException("The live job page returned HTTP " + status + ".");
            }
            String contentType = response.headers().firstValue("content-type").orElse("text/html")
                    .toLowerCase(Locale.ROOT);
            if (!contentType.contains("text/html") && !contentType.contains("application/xhtml+xml")) {
                close(response.body());
                throw new IllegalStateException("The direct job link did not return an HTML page.");
            }
            byte[] bytes = readLimited(response.body());
            String html = new String(bytes, StandardCharsets.UTF_8);
            ExtractedContent extracted = extractDescription(html, current.toString());
            return new FetchedDescription(current.toString(), extracted.description(), extracted.method());
        }
        throw new IllegalStateException("The live job page redirected too many times.");
    }

    ExtractedContent extractDescription(String html, String baseUri) {
        Document document = Jsoup.parse(html, baseUri);
        String structured = structuredDescription(document);
        if (isCredibleDescription(structured, false)) {
            return new ExtractedContent(structured, "JSON_LD_JOB_POSTING");
        }

        var selected = document.select(DESCRIPTION_SELECTORS).stream()
                .map(this::cleanText)
                .filter(value -> isCredibleDescription(value, false))
                .max(Comparator.comparingInt(String::length));
        if (selected.isPresent()) return new ExtractedContent(selected.get(), "JOB_DESCRIPTION_CONTAINER");

        var semantic = document.select("main, article").stream()
                .map(this::cleanText)
                .filter(value -> isCredibleDescription(value, true))
                .max(Comparator.comparingInt(String::length));
        if (semantic.isPresent()) return new ExtractedContent(semantic.get(), "SEMANTIC_PAGE_CONTENT");

        throw new IllegalStateException("A complete job-description section could not be identified on the live page.");
    }

    private String structuredDescription(Document document) {
        String longest = "";
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = objectMapper.readTree(script.data());
                Deque<JsonNode> pending = new ArrayDeque<>();
                pending.add(root);
                while (!pending.isEmpty()) {
                    JsonNode node = pending.removeFirst();
                    if (node.isArray() || node.isObject()) {
                        for (JsonNode child : node) if (child.isArray() || child.isObject()) pending.addLast(child);
                    }
                    if (node.isObject()) {
                        if (isJobPosting(node) && node.path("description").isTextual()) {
                            String description = normalize(Jsoup.parseBodyFragment(node.path("description").asText()).text());
                            if (description.length() > longest.length()) longest = description;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Pages frequently include unrelated or malformed JSON-LD blocks; other extractors remain available.
            }
        }
        return longest;
    }

    private boolean isJobPosting(JsonNode node) {
        JsonNode type = node.path("@type");
        if (type.isTextual()) return "jobposting".equalsIgnoreCase(type.asText());
        if (type.isArray()) {
            for (JsonNode value : type) if ("jobposting".equalsIgnoreCase(value.asText())) return true;
        }
        return false;
    }

    private String cleanText(Element element) {
        Element copy = element.clone();
        copy.select("script, style, noscript, svg, nav, header, footer, form, button").remove();
        return normalize(copy.text());
    }

    private boolean isCredibleDescription(String text, boolean requireSectionMarker) {
        if (text == null || text.length() < MIN_DESCRIPTION_CHARACTERS) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("access denied") || lower.contains("enable javascript to run this app")) return false;
        if (!requireSectionMarker) return true;
        return lower.matches(".*\\b(responsibilities|qualifications|requirements|what you will do|about the role|experience)\\b.*");
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private URI validatedUri(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A direct job link is required.");
        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("The direct job link is not a valid URL.", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("https") && !scheme.equals("http")) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Only public HTTP or HTTPS job links can be fetched.");
        }
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            throw new IllegalArgumentException("Only standard HTTP or HTTPS ports can be fetched.");
        }
        String host = uri.getHost();
        if (host.equalsIgnoreCase("localhost") || host.toLowerCase(Locale.ROOT).endsWith(".localhost")) {
            throw new IllegalArgumentException("Local network addresses cannot be fetched.");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivate(address)) throw new IllegalArgumentException("Private network addresses cannot be fetched.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("The direct job-link host could not be resolved.", exception);
        }
        return uri;
    }

    private boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        if (address instanceof Inet6Address) {
            byte[] raw = address.getAddress();
            return (raw[0] & 0xfe) == 0xfc;
        }
        return false;
    }

    private byte[] readLimited(InputStream body) {
        try (body) {
            byte[] bytes = body.readNBytes(MAX_PAGE_BYTES + 1);
            if (bytes.length > MAX_PAGE_BYTES) {
                throw new IllegalStateException("The live job page is too large to store safely.");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("The live job page could not be read.", exception);
        }
    }

    private void close(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
            // The response is already being discarded.
        }
    }

    public record FetchedDescription(String finalUrl, String description, String extractionMethod) {}
    record ExtractedContent(String description, String method) {}
}
