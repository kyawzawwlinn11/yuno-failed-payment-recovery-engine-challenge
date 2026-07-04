package com.kyawzawwlinn.yuno.paymentrecovery.model

import java.math.BigDecimal
import java.time.Instant

data class FailedTransaction(
    val transactionId: String,
    val customerId: String?,
    val country: Country,
    val amount: BigDecimal,
    val currency: String,
    val amountUsdEquivalent: BigDecimal,
    val paymentMethod: PaymentMethod,
    val failureReason: FailureReason,
    val recoverabilityScore: Int,
    val recoveryProbability: Double,
    val recommendation: String,
    val recommendedRetryAt: Instant?,
    val occurredAt: Instant,
    val createdAt: Instant,
)
