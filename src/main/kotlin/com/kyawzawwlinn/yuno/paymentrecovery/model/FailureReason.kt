package com.kyawzawwlinn.yuno.paymentrecovery.model

enum class FailureReason(
    val value: String,
    val category: FailureCategory,
) {
    INSUFFICIENT_FUNDS("insufficient_funds", FailureCategory.SOFT),
    ISSUER_UNAVAILABLE("issuer_unavailable", FailureCategory.SOFT),
    TIMEOUT("timeout", FailureCategory.SOFT),
    TRY_AGAIN_LATER("try_again_later", FailureCategory.SOFT),
    DO_NOT_HONOR("do_not_honor", FailureCategory.SOFT),

    STOLEN_CARD("stolen_card", FailureCategory.HARD),
    CARD_EXPIRED("card_expired", FailureCategory.HARD),
    INVALID_CARD("invalid_card", FailureCategory.HARD),
    BLOCKED_CARD("blocked_card", FailureCategory.HARD),
    FRAUDULENT("fraudulent", FailureCategory.HARD),

    GATEWAY_ERROR("gateway_error", FailureCategory.TECHNICAL),
    NETWORK_ERROR("network_error", FailureCategory.TECHNICAL),
    INVALID_REQUEST("invalid_request", FailureCategory.TECHNICAL),
    ;

    companion object {
        fun fromValue(value: String): FailureReason =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException(
                    "failureReason must be one of: ${entries.joinToString { it.value }}",
                )
    }
}
