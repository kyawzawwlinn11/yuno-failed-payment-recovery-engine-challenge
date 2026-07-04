package com.kyawzawwlinn.yuno.paymentrecovery.dto

import java.math.BigDecimal
import java.time.Instant
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank

data class CreateFailedTransactionRequest(
    @field:NotBlank
    val transactionId: String,
    @field:DecimalMin("0.01")
    val amount: BigDecimal,
    @field:NotBlank
    val currency: String,
    @field:NotBlank
    val country: String,
    @field:NotBlank
    val failureReason: String,
    @field:NotBlank
    val paymentMethod: String,
    val timestamp: Instant? = null,
    val customerId: String? = null,
)

data class FailedTransactionResponse(
    val transactionId: String,
    val customerId: String?,
    val country: String,
    val amount: BigDecimal,
    val currency: String,
    val amountUsdEquivalent: BigDecimal,
    val paymentMethod: String,
    val failureReason: String,
    val failureCategory: String,
    val recoverabilityScore: Int,
    val recoveryProbability: Double,
    val recommendation: String,
    val recommendedRetryAt: Instant?,
    val timestamp: Instant,
    val createdAt: Instant,
)

data class RecoveryCandidateResponse(
    val transaction: FailedTransactionResponse,
    val recoverabilityScore: Int,
    val priority: BigDecimal,
    val priorityRank: Int,
    val recommendation: String,
    val reasons: List<String>,
)

data class RecoveryCandidatesResponse(
    val totalStoredTransactions: Int,
    val totalCandidates: Int,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val candidates: List<RecoveryCandidateResponse>,
)

data class InsightsResponse(
    val totalTransactions: Int,
    val recoveryCandidateCount: Int,
    val totalRecoverableRevenueAtRisk: BigDecimal,
    val estimatedRecoveryValue: BigDecimal,
    val breakdownByFailureCategory: Map<String, BigDecimal>,
    val breakdownByFailureReason: Map<String, BigDecimal>,
    val opportunityByCountry: Map<String, BigDecimal>,
)

data class SeedResponse(
    val insertedCount: Int,
    val message: String,
)

data class RetrySimulationRequest(
    val transactionId: String,
)

data class RetrySimulationResponse(
    val transactionId: String,
    val recoverabilityScore: Int,
    val recommendation: String,
    val simulatedOutcome: String,
)

data class ErrorResponse(
    val message: String,
    val timestamp: Instant = Instant.now(),
)
