package ng.mint.ocrscanner.model

sealed class CardResult {
    data class Success(val data: CardResponse) : CardResult()
    data class Error(
        val errorType: ErrorType,
        val message: String? = null
    ) : CardResult()
    object EmptyState : CardResult()
    object Loading : CardResult()

    enum class ErrorType {
        NETWORK,
        RATE_LIMIT,
        NOT_FOUND,
        SERVER_ERROR,
        UNKNOWN
    }
}
