package dev.dhruv.jobsearch.skill;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import dev.dhruv.jobsearch.connected.ConnectedBrokerClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LiveJobPageFetcher {

    private static final int MIN_DESCRIPTION_CHARACTERS = 300;
    private static final String DESCRIPTION_SELECTORS = String.join(", ",
            "[data-automation-id=jobPostingDescription]", "[data-testid=job-description]",
            "[data-testid=jobDescription]", "#job-description", "#jobDescriptionText",
            ".job-description", ".jobDescription", ".posting-description", ".description__text",
            ".show-more-less-html", ".posting-page .content", "[class*=job-description]",
            "[class*=jobDescription]");

    private final ObjectMapper objectMapper;
    private final ConnectedBrokerClient broker;

    @Autowired
    public LiveJobPageFetcher(ObjectMapper objectMapper, ConnectedBrokerClient broker) {
        this.objectMapper = objectMapper;
        this.broker = broker;
    }

    LiveJobPageFetcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.broker = null;
    }

    public FetchedDescription fetch(String sourceUrl) {
        ConnectedBrokerClient.JobPageResponse response = broker.fetchJobPage(sourceUrl);
        String html = new String(response.body(), StandardCharsets.UTF_8);
        ExtractedContent extracted = extractDescription(html, response.finalUrl());
        return new FetchedDescription(response.finalUrl(), extracted.description(), extracted.method());
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

    public record FetchedDescription(String finalUrl, String description, String extractionMethod) {}
    record ExtractedContent(String description, String method) {}
}
