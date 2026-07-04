package com.kyawzawwlinn.yuno.paymentrecovery.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/health")
    fun health(): Map<String, String> =
        mapOf(
            "status" to "UP",
            "service" to "failed-payment-recovery-engine",
        )
}
