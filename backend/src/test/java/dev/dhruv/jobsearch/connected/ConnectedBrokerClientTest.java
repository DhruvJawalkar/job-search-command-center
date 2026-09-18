package dev.dhruv.jobsearch.connected;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConnectedBrokerClientTest {

    private static final String TOKEN = "test-token-that-is-at-least-thirty-two-characters";

    @Test
    void rejectsAnArbitraryBrokerOrigin() {
        assertThatThrownBy(() -> new ConnectedBrokerClient(true, "http://unreviewed-proxy.example:8787", TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reviewed egress-broker service");
    }

    @Test
    void rejectsTheBrokerServiceOnAnUnexpectedPort() {
        assertThatThrownBy(() -> new ConnectedBrokerClient(true, "http://egress-broker:9000", TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reviewed internal broker port");
    }
}
