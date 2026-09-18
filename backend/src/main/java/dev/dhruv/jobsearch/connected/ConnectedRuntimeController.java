package dev.dhruv.jobsearch.connected;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connected-runtime")
public class ConnectedRuntimeController {
    private final TransmissionService transmissions;

    public ConnectedRuntimeController(TransmissionService transmissions) { this.transmissions = transmissions; }

    @GetMapping
    RuntimeView runtime() {
        return new RuntimeView(transmissions.connectedRuntimeEnabled(),
                transmissions.connectedRuntimeEnabled() ? "REVIEWED_BROKER" : "LOCAL_ONLY");
    }

    @GetMapping("/transmission-receipts")
    List<TransmissionService.ReceiptView> receipts() { return transmissions.recentReceipts(); }

    public record RuntimeView(boolean connectedRuntimeEnabled, String outboundBoundary) {}
}
