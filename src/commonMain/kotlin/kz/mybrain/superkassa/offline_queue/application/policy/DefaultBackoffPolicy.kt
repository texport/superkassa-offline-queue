package kz.mybrain.superkassa.offline_queue.application.policy

/**
 * Экспоненциальный backoff с верхним пределом.
 */
class DefaultBackoffPolicy(
    private val baseDelayMs: Long = 1000,
    private val maxDelayMs: Long = 60000
) : BackoffPolicy {
    override fun nextAttemptAt(now: Long, attempt: Int): Long {
        val multiplier = 1L shl attempt.coerceAtMost(10)
        val delay = (baseDelayMs * multiplier).coerceAtMost(maxDelayMs)
        return now + delay
    }
}
