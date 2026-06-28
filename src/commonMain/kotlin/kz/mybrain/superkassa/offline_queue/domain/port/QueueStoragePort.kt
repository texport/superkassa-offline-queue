package kz.mybrain.superkassa.offline_queue.domain.port

import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommand
import kz.mybrain.superkassa.offline_queue.domain.model.QueueLane
import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus

/**
 * Порт хранения очереди.
 */
interface QueueStoragePort {
    fun enqueue(command: QueueCommand): Boolean
    fun nextPending(cashboxId: String, lane: QueueLane, now: Long): QueueCommand?
    fun updateStatus(
        id: String,
        status: QueueStatus,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?
    ): Boolean
    fun markInProgress(id: String, now: Long): Boolean
    fun listByCashbox(cashboxId: String, lane: QueueLane, limit: Int, offset: Int = 0): List<QueueCommand>
    fun deleteByCashbox(cashboxId: String): Boolean
}
