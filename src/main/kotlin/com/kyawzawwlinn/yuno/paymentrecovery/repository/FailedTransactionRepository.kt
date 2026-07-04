package com.kyawzawwlinn.yuno.paymentrecovery.repository

import com.kyawzawwlinn.yuno.paymentrecovery.model.FailedTransaction
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class FailedTransactionRepository {
    private val transactions = ConcurrentHashMap<String, FailedTransaction>()

    fun save(transaction: FailedTransaction): FailedTransaction {
        transactions[transaction.transactionId] = transaction
        return transaction
    }

    fun findByTransactionId(transactionId: String): FailedTransaction? =
        transactions[transactionId]

    fun findAll(): List<FailedTransaction> =
        transactions.values.sortedByDescending { it.createdAt }

    fun countByCustomerId(customerId: String): Int =
        transactions.values.count { it.customerId == customerId }

    fun clear() {
        transactions.clear()
    }

    fun saveAll(transactions: List<FailedTransaction>): List<FailedTransaction> {
        transactions.forEach { save(it) }
        return transactions
    }
}
