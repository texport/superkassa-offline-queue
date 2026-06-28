package kz.mybrain.superkassa.offline_queue.domain.model

/**
 * Элемент очереди для отправки команд в ОФД.
 */
data class QueueCommand(
    val id: String,
    val cashboxId: String,
    val lane: QueueLane,
    val type: QueueCommandType,
    val payloadRef: String,
    val createdAt: Long,
    val status: QueueStatus,
    val attempt: Int,
    val nextAttemptAt: Long? = null,
    val lastError: String? = null
)
