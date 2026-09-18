package dev.dhruv.jobsearch.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import dev.dhruv.jobsearch.connected.ConnectedBrokerClient;

class LiveJobPageFetcherTest {

    private final LiveJobPageFetcher fetcher = new LiveJobPageFetcher(new ObjectMapper());

    @Test
    void prefersStructuredJobPostingDescriptions() {
        String html = """
                <html><head><script type="application/ld+json">
                {"@context":"https://schema.org","@type":"JobPosting","description":"<h2>Responsibilities</h2><p>Design distributed Java services and Kafka pipelines. Own architecture, implementation, testing, deployment, observability, incident response, security, performance, and technical leadership across production services.</p><h2>Qualifications</h2><p>Seven years of backend engineering, Kubernetes, cloud operations, reliability, security, testing, mentoring, and production incident response experience are required for this senior platform role.</p>"}
                </script></head><body><main>Generic careers navigation</main></body></html>
                """;

        var result = fetcher.extractDescription(html, "https://jobs.example.com/42");

        assertThat(result.method()).isEqualTo("JSON_LD_JOB_POSTING");
        assertThat(result.description()).contains("Design distributed Java services", "Qualifications");
    }

    @Test
    void extractsARecognizedJobDescriptionContainer() {
        String html = """
                <html><body><nav>Careers Home</nav><section data-automation-id="jobPostingDescription">
                <h2>About the role</h2><p>Build a cloud data platform using Java, Spark, Kafka, and Kubernetes.</p>
                <h2>Responsibilities</h2><p>Own architecture, implementation, testing, deployment, observability, incident response, security, performance, and technical leadership across production services.</p>
                <h2>Qualifications</h2><p>Experience designing large-scale distributed systems and reliable APIs is required. Experience with streaming data systems, infrastructure as code, and mentoring engineers is preferred.</p>
                </section></body></html>
                """;

        var result = fetcher.extractDescription(html, "https://jobs.example.com/84");

        assertThat(result.method()).isEqualTo("JOB_DESCRIPTION_CONTAINER");
        assertThat(result.description()).doesNotContain("Careers Home").contains("Spark", "Qualifications");
    }

    @Test
    void delegatesNetworkAccessToTheReviewedBroker() {
        ConnectedBrokerClient broker = mock(ConnectedBrokerClient.class);
        String html = "<main><h2>Responsibilities</h2><p>" + "Build secure distributed systems. ".repeat(20)
                + "</p><h2>Qualifications</h2><p>Production Java and cloud experience.</p></main>";
        when(broker.fetchJobPage("https://jobs.example.com/42"))
                .thenReturn(new ConnectedBrokerClient.JobPageResponse("https://jobs.example.com/42",
                        html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        var result = new LiveJobPageFetcher(new ObjectMapper(), broker).fetch("https://jobs.example.com/42");

        assertThat(result.finalUrl()).isEqualTo("https://jobs.example.com/42");
        assertThat(result.description()).contains("Build secure distributed systems", "Qualifications");
    }
}
