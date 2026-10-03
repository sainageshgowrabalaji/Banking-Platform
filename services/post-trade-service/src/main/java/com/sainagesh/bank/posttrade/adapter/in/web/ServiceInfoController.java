package com.sainagesh.bank.posttrade.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Says which service this is and how far it has been built. Replaced by real endpoints phase by phase. */
@RestController
public class ServiceInfoController {

    public record ServiceInfo(String service, String area, int phase, String status) {}

    @GetMapping("/v1/info")
    public ServiceInfo info() {
        return new ServiceInfo("post-trade-service", "Markets", 3, "skeleton");
    }
}
