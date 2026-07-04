package com.kyawzawwlinn.yuno.paymentrecovery.controller

import com.kyawzawwlinn.yuno.paymentrecovery.dto.CreateFailedTransactionRequest
import com.kyawzawwlinn.yuno.paymentrecovery.dto.FailedTransactionResponse
import com.kyawzawwlinn.yuno.paymentrecovery.dto.InsightsResponse
import com.kyawzawwlinn.yuno.paymentrecovery.dto.RecoveryCandidatesResponse
import com.kyawzawwlinn.yuno.paymentrecovery.dto.SeedResponse
import com.kyawzawwlinn.yuno.paymentrecovery.service.FailedTransactionService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/transactions")
class TransactionController(
    private val failedTransactionService: FailedTransactionService,
) {
    @PostMapping("/failed")
    fun createFailedTransaction(
        @Valid @RequestBody request: CreateFailedTransactionRequest,
    ): FailedTransactionResponse =
        failedTransactionService.ingest(request)

    @GetMapping("/recovery-candidates")
    fun getRecoveryCandidates(
        @RequestParam(required = false) minAmount: BigDecimal?,
        @RequestParam(required = false) maxAgeHours: Long?,
        @RequestParam(required = false) country: String?,
        @RequestParam(defaultValue = "50") minScore: Int,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): RecoveryCandidatesResponse =
        failedTransactionService.findRecoveryCandidates(
            minAmount = minAmount,
            maxAgeHours = maxAgeHours,
            country = country,
            minScore = minScore,
            page = page,
            size = size,
        )

    @GetMapping("/insights")
    fun getInsights(): InsightsResponse =
        failedTransactionService.getInsights()

    @PostMapping("/seed")
    fun seedTransactions(): SeedResponse =
        failedTransactionService.seedTransactions()
}
