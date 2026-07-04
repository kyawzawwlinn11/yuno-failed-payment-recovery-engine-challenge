package com.kyawzawwlinn.yuno.paymentrecovery.model

enum class Country(
    val value: String,
    val currency: String,
) {
    ARGENTINA("argentina", "ARS"),
    CHILE("chile", "CLP"),
    URUGUAY("uruguay", "UYU"),
    ;

    companion object {
        fun fromValue(value: String): Country =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException(
                    "country must be one of: ${entries.joinToString { it.value }}",
                )
    }
}
