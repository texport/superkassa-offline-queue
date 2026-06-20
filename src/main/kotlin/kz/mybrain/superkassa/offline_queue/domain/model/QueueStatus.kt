package kz.mybrain.superkassa.offline_queue.domain.model

/**
 * Статус элемента очереди.
 */
enum class QueueStatus {
    PENDING,
    IN_PROGRESS,
    SENT,
    FAILED
}
