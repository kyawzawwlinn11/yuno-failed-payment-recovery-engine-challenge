package com.kyawzawwlinn.yuno.paymentrecovery.model

enum class PaymentMethod(val value: String) {
    VISA_CREDIT("visa_credit"),
    MASTERCARD_CREDIT("mastercard_credit"),
    DEBIT_CARD("debit_card"),
    MERCADO_PAGO("mercado_pago"),
    ;

    companion object {
        fun fromValue(value: String): PaymentMethod =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException(
                    "paymentMethod must be one of: ${entries.joinToString { it.value }}",
                )
    }
}
