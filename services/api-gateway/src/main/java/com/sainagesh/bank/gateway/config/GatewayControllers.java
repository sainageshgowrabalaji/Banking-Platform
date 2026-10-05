package com.sainagesh.bank.gateway.config;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The two things the gateway answers by itself. Who it is, and what to say when a service is down. */
@RestController
public class GatewayControllers {

    public record ServiceInfo(String service, String area, int phase, String status) {}

    @GetMapping("/v1/info")
    public ServiceInfo info() {
        return new ServiceInfo("api-gateway", "Edge", 1, "routes, tokens, rate limits, timeouts and circuit breakers");
    }

    /**
     * Where a circuit breaker sends a request when its service failed, timed out, or is being rested.
     * The client gets the same problem details format as every other error, and is told when to retry.
     */
    @RequestMapping("/fallback/{service}")
    public ResponseEntity<ProblemDetail> fallback(@PathVariable String service) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "The " + service + " service is not answering right now. Try again shortly.");
        problem.setType(URI.create("https://errors.bank.example/SERVICE_UNAVAILABLE"));
        problem.setTitle("Service unavailable");
        problem.setProperty("code", "SERVICE_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
