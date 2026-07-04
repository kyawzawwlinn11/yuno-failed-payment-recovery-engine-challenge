package com.kyawzawwlinn.yuno.paymentrecovery.service

import com.kyawzawwlinn.yuno.paymentrecovery.dto.CreateFailedTransactionRequest
import com.kyawzawwlinn.yuno.paymentrecovery.dto.FailedTransactionResponse
import com.kyawzawwlinn.yuno.paymentrecovery.dto.InsightsResponse
import com.kyawzawwlinn.yuno.paymentrecovery.dto.RecoveryCandidateResponse
import com.kyawzawwlinn.yuno.paymentrecovery.dto.RecoveryCandidatesResponse
import com.kyawzawwlinn.yuno.paymentrecovery.dto.SeedResponse
import com.kyawzawwlinn.yuno.paymentrecovery.model.Country
import com.kyawzawwlinn.yuno.paymentrecovery.model.FailedTransaction
import com.kyawzawwlinn.yuno.paymentrecovery.model.FailureCategory
import com.kyawzawwlinn.yuno.paymentrecovery.model.FailureReason
import com.kyawzawwlinn.yuno.paymentrecovery.model.PaymentMethod
import com.kyawzawwlinn.yuno.paymentrecovery.repository.FailedTransactionRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@Service
class FailedTransactionService(
    private val repository: FailedTransactionRepository,
) {
    fun ingest(request: CreateFailedTransactionRequest): FailedTransactionResponse {
        repository.findByTransactionId(request.transactionId)?.let {
            return it.toResponse()
        }

        val country = Country.fromValue(request.country)
        val paymentMethod = PaymentMethod.fromValue(request.paymentMethod)
        val failureReason = FailureReason.fromValue(request.failureReason)
        val amount = request.amount.setScale(2, RoundingMode.HALF_UP)
        val currency = normalizeCurrency(request.currency)
        val amountUsdEquivalent = calculateAmountUsdEquivalent(amount, currency, country)
        val occurredAt = request.timestamp ?: Instant.now()
        val previousCustomerFailures = request.customerId?.let { repository.countByCustomerId(it) } ?: 0
        val recoverabilityScore = calculateRecoverabilityScore(
            failureReason = failureReason,
            paymentMethod = paymentMethod,
            amountUsdEquivalent = amountUsdEquivalent,
            occurredAt = occurredAt,
            previousCustomerFailures = previousCustomerFailures,
        )
        val recommendation = calculateRecommendation(failureReason, recoverabilityScore)
        val transaction = FailedTransaction(
            transactionId = request.transactionId,
            customerId = request.customerId,
            country = country,
            amount = amount,
            currency = currency,
            amountUsdEquivalent = amountUsdEquivalent,
            paymentMethod = paymentMethod,
            failureReason = failureReason,
            recoverabilityScore = recoverabilityScore,
            recoveryProbability = calculateRecoveryProbability(recoverabilityScore),
            recommendation = recommendation,
            recommendedRetryAt = calculateRecommendedRetryAt(failureReason, occurredAt, Instant.now()),
            occurredAt = occurredAt,
            createdAt = Instant.now(),
        )

        return repository.save(transaction).toResponse()
    }

    fun findRecoveryCandidates(
        minAmount: BigDecimal?,
        maxAgeHours: Long?,
        country: String?,
        minScore: Int,
        page: Int,
        size: Int,
    ): RecoveryCandidatesResponse {
        val normalizedPage = page.coerceAtLeast(0)
        val normalizedSize = size.coerceIn(1, 100)
        val countryFilter = country?.let { Country.fromValue(it) }
        val now = Instant.now()

        val candidates = repository.findAll()
            .asSequence()
            .filter { it.isRecoverable(minScore) }
            .filter { minAmount == null || it.amountUsdEquivalent >= minAmount }
            .filter { countryFilter == null || it.country == countryFilter }
            .filter {
                maxAgeHours == null ||
                    ChronoUnit.HOURS.between(it.occurredAt, now).coerceAtLeast(0) <= maxAgeHours
            }
            .map { it.toRecoveryCandidateResponse() }
            .sortedWith(
                compareByDescending<RecoveryCandidateResponse> { it.priority }
                    .thenByDescending { it.transaction.timestamp },
            )
            .mapIndexed { index, candidate ->
                candidate.copy(priorityRank = index + 1)
            }
            .toList()

        val fromIndex = (normalizedPage * normalizedSize).coerceAtMost(candidates.size)
        val toIndex = (fromIndex + normalizedSize).coerceAtMost(candidates.size)
        val pagedCandidates = candidates.subList(fromIndex, toIndex)
        val totalPages = if (candidates.isEmpty()) {
            0
        } else {
            ((candidates.size - 1) / normalizedSize) + 1
        }

        return RecoveryCandidatesResponse(
            totalStoredTransactions = repository.findAll().size,
            totalCandidates = candidates.size,
            page = normalizedPage,
            size = normalizedSize,
            totalPages = totalPages,
            candidates = pagedCandidates,
        )
    }

    fun getInsights(): InsightsResponse {
        val transactions = repository.findAll()
        val recoverableTransactions = transactions.filter { it.isRecoverable(minScore = 50) }
        val softDeclines = transactions.filter { it.failureReason.category == FailureCategory.SOFT }

        return InsightsResponse(
            totalTransactions = transactions.size,
            recoveryCandidateCount = recoverableTransactions.size,
            totalRecoverableRevenueAtRisk = recoverableTransactions.sumAmounts(),
            estimatedRecoveryValue = softDeclines
                .sumAmounts()
                .multiply(BigDecimal("0.40"))
                .setScale(2, RoundingMode.HALF_UP),
            breakdownByFailureCategory = transactions
                .groupBy { it.failureReason.category.name.lowercase() }
                .mapValues { (_, categoryTransactions) -> categoryTransactions.sumAmounts() },
            breakdownByFailureReason = transactions
                .groupBy { it.failureReason.value }
                .mapValues { (_, reasonTransactions) -> reasonTransactions.sumAmounts() },
            opportunityByCountry = recoverableTransactions
                .groupBy { it.country.value }
                .mapValues { (_, countryTransactions) -> countryTransactions.sumAmounts() },
        )
    }

    fun seedTransactions(): SeedResponse {
        repository.clear()

        val random = Random(42)
        val now = Instant.now()
        val softReasons = FailureReason.entries.filter { it.category == FailureCategory.SOFT }
        val hardReasons = FailureReason.entries.filter { it.category == FailureCategory.HARD }
        val technicalReasons = FailureReason.entries.filter { it.category == FailureCategory.TECHNICAL }
        val seedReasons = buildList {
            addAll(cycledReasons(softReasons, 120))
            addAll(cycledReasons(hardReasons, 90))
            addAll(cycledReasons(technicalReasons, 90))
        }.shuffled(random)
        val customerFailureCounts = mutableMapOf<String, Int>()

        val transactions = seedReasons.mapIndexed { index, failureReason ->
            val country = Country.entries[index % Country.entries.size]
            val paymentMethod = PaymentMethod.entries[index % PaymentMethod.entries.size]
            val amountUsdEquivalent = BigDecimal(random.nextDouble(5.0, 800.0))
                .setScale(2, RoundingMode.HALF_UP)
            val amount = calculateLocalAmount(amountUsdEquivalent, country.currency)
            val customerId = seedCustomerId(index)
            val previousCustomerFailures = customerFailureCounts[customerId] ?: 0
            val occurredAt = now.minus(random.nextLong(0, 7 * 24 + 1), ChronoUnit.HOURS)
            val recoverabilityScore = calculateRecoverabilityScore(
                failureReason = failureReason,
                paymentMethod = paymentMethod,
                amountUsdEquivalent = amountUsdEquivalent,
                occurredAt = occurredAt,
                previousCustomerFailures = previousCustomerFailures,
            )
            customerFailureCounts[customerId] = previousCustomerFailures + 1

            FailedTransaction(
                transactionId = "seed-txn-${(index + 1).toString().padStart(3, '0')}",
                customerId = customerId,
                country = country,
                amount = amount,
                currency = country.currency,
                amountUsdEquivalent = amountUsdEquivalent,
                paymentMethod = paymentMethod,
                failureReason = failureReason,
                recoverabilityScore = recoverabilityScore,
                recoveryProbability = calculateRecoveryProbability(recoverabilityScore),
                recommendation = calculateRecommendation(failureReason, recoverabilityScore),
                recommendedRetryAt = calculateRecommendedRetryAt(failureReason, occurredAt, now),
                occurredAt = occurredAt,
                createdAt = now,
            )
        }

        repository.saveAll(transactions)

        return SeedResponse(
            insertedCount = transactions.size,
            message = "Seeded ${transactions.size} failed transactions across the last 7 days.",
        )
    }

    private fun calculateRecoverabilityScore(
        failureReason: FailureReason,
        paymentMethod: PaymentMethod,
        amountUsdEquivalent: BigDecimal,
        occurredAt: Instant,
        previousCustomerFailures: Int,
    ): Int {
        var score = 50

        score += when (failureReason.category) {
            FailureCategory.SOFT -> if (failureReason == FailureReason.DO_NOT_HONOR) 10 else 25
            FailureCategory.HARD -> if (failureReason == FailureReason.CARD_EXPIRED) 5 else -35
            FailureCategory.TECHNICAL -> if (failureReason == FailureReason.INVALID_REQUEST) -25 else 20
        }

        score += when (ChronoUnit.DAYS.between(occurredAt, Instant.now()).coerceAtLeast(0)) {
            0L, 1L -> 15
            2L, 3L -> 8
            in 4L..7L -> 0
            else -> -15
        }

        score += when {
            amountUsdEquivalent > BigDecimal("300") -> 12
            amountUsdEquivalent > BigDecimal("100") -> 8
            else -> 0
        }

        score += when (paymentMethod) {
            PaymentMethod.MERCADO_PAGO -> 8
            PaymentMethod.DEBIT_CARD -> 2
            PaymentMethod.VISA_CREDIT,
            PaymentMethod.MASTERCARD_CREDIT,
            -> 0
        }

        if (previousCustomerFailures > 0) {
            score += 8
        }

        if (previousCustomerFailures > 2) {
            score -= ((previousCustomerFailures - 2) * 5).coerceAtMost(20)
        }

        return score.coerceIn(0, 100)
    }

    private fun calculateRecoveryProbability(recoverabilityScore: Int): Double =
        BigDecimal(recoverabilityScore)
            .divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            .toDouble()

    private fun cycledReasons(
        reasons: List<FailureReason>,
        count: Int,
    ): List<FailureReason> =
        List(count) { index -> reasons[index % reasons.size] }

    private fun seedCustomerId(index: Int): String =
        if (index < 180) {
            "repeat-customer-${(index % 45 + 1).toString().padStart(3, '0')}"
        } else {
            "first-time-customer-${(index - 179).toString().padStart(3, '0')}"
        }

    private fun normalizeCurrency(currency: String): String =
        currency.uppercase()

    private fun calculateAmountUsdEquivalent(
        amount: BigDecimal,
        currency: String,
        country: Country,
    ): BigDecimal {
        if (currency != country.currency && currency != "USD") {
            throw IllegalArgumentException("currency must be ${country.currency} for ${country.value}, or USD")
        }

        return amount.divide(currencyToUsdRate(currency), 2, RoundingMode.HALF_UP)
    }

    private fun calculateLocalAmount(
        amountUsdEquivalent: BigDecimal,
        currency: String,
    ): BigDecimal =
        amountUsdEquivalent
            .multiply(currencyToUsdRate(currency))
            .setScale(2, RoundingMode.HALF_UP)

    private fun currencyToUsdRate(currency: String): BigDecimal =
        when (currency) {
            "USD" -> BigDecimal.ONE
            "ARS" -> BigDecimal("1000")
            "CLP" -> BigDecimal("950")
            "UYU" -> BigDecimal("40")
            else -> throw IllegalArgumentException("currency must be one of: ARS, CLP, UYU, USD")
        }

    private fun calculateRecommendedRetryAt(
        failureReason: FailureReason,
        occurredAt: Instant,
        now: Instant,
    ): Instant? {
        val calculated = when (failureReason) {
            FailureReason.STOLEN_CARD,
            FailureReason.INVALID_CARD,
            FailureReason.BLOCKED_CARD,
            FailureReason.FRAUDULENT,
            FailureReason.INVALID_REQUEST,
            -> return null

            FailureReason.INSUFFICIENT_FUNDS,
            FailureReason.DO_NOT_HONOR,
            -> occurredAt.plus(1, ChronoUnit.DAYS)

            FailureReason.CARD_EXPIRED -> occurredAt.plus(2, ChronoUnit.DAYS)

            FailureReason.ISSUER_UNAVAILABLE,
            FailureReason.TIMEOUT,
            FailureReason.TRY_AGAIN_LATER,
            FailureReason.GATEWAY_ERROR,
            FailureReason.NETWORK_ERROR,
            -> occurredAt.plus(1, ChronoUnit.HOURS)
        }

        return if (calculated.isBefore(now)) now else calculated
    }

    private fun calculateRecommendation(
        failureReason: FailureReason,
        recoverabilityScore: Int,
    ): String {
        if (
            failureReason == FailureReason.STOLEN_CARD ||
            failureReason == FailureReason.INVALID_CARD ||
            failureReason == FailureReason.BLOCKED_CARD ||
            failureReason == FailureReason.FRAUDULENT ||
            failureReason == FailureReason.INVALID_REQUEST
        ) {
            return "do_not_retry"
        }

        return when {
            recoverabilityScore >= 80 -> "retry_now"
            recoverabilityScore >= 60 -> "retry_later"
            recoverabilityScore >= 40 -> "request_payment_update"
            else -> "manual_review"
        }
    }

    private fun FailedTransaction.isRecoverable(minScore: Int): Boolean =
        recoverabilityScore >= minScore &&
            failureReason != FailureReason.STOLEN_CARD &&
            failureReason != FailureReason.INVALID_CARD &&
            failureReason != FailureReason.BLOCKED_CARD &&
            failureReason != FailureReason.FRAUDULENT &&
            failureReason != FailureReason.INVALID_REQUEST

    private fun FailedTransaction.toRecoveryCandidateResponse(): RecoveryCandidateResponse =
        RecoveryCandidateResponse(
            transaction = toResponse(),
            recoverabilityScore = recoverabilityScore,
            priority = amountUsdEquivalent
                .multiply(BigDecimal(recoverabilityScore))
                .setScale(2, RoundingMode.HALF_UP),
            priorityRank = 0,
            recommendation = recommendation,
            reasons = recoveryReasons(),
        )

    private fun FailedTransaction.recoveryReasons(): List<String> =
        buildList {
            add("${failureReason.category.name.lowercase()} failure: ${failureReason.value}")
            add("score $recoverabilityScore meets recovery threshold")
            if (recommendedRetryAt != null) {
                add("recommended retry at $recommendedRetryAt")
            }
        }

    private fun List<FailedTransaction>.sumAmounts(): BigDecimal =
        fold(BigDecimal.ZERO) { total, transaction ->
            total + transaction.amountUsdEquivalent
        }.setScale(2, RoundingMode.HALF_UP)

    private fun FailedTransaction.toResponse(): FailedTransactionResponse =
        FailedTransactionResponse(
            transactionId = transactionId,
            customerId = customerId,
            country = country.value,
            amount = amount,
            currency = currency,
            amountUsdEquivalent = amountUsdEquivalent,
            paymentMethod = paymentMethod.value,
            failureReason = failureReason.value,
            failureCategory = failureReason.category.name.lowercase(),
            recoverabilityScore = recoverabilityScore,
            recoveryProbability = recoveryProbability,
            recommendation = recommendation,
            recommendedRetryAt = recommendedRetryAt,
            timestamp = occurredAt,
            createdAt = createdAt,
        )
}
