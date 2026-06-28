package kz.mybrain.superkassa.offline_queue.application.model

import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus

/**
 * Результат обработки команды очереди.
 */
data class DispatchResult(
    val status: QueueStatus,
    val errorMessage: String? = null,
    val retryAt: Long? = null
)
