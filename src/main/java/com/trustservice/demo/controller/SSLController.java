package com.trustservice.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// it works. http://localhost:8443  does not work as the endpoint needs the client to use TLS.
// https://localhost:8443 or https://127.0.0.1:8443/ works and returns the Welcome value.
// However, firefox does not trust the certificate. We can accept the risk and checkout our chain of trust in the browser.

@RestController
@RequestMapping("/")
class SSLController {
    @GetMapping
    public String welcome() {
        return "🔒 Hello from Spring Boot SSL Server!";
    }
}
