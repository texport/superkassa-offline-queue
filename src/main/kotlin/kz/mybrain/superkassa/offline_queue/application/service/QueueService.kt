package kz.mybrain.superkassa.offline_queue.application.service

import kz.mybrain.superkassa.offline_queue.application.model.DispatchResult
import kz.mybrain.superkassa.offline_queue.application.policy.BackoffPolicy
import kz.mybrain.superkassa.offline_queue.application.policy.SystemTimeProvider
import kz.mybrain.superkassa.offline_queue.application.policy.TimeProvider
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommand
import kz.mybrain.superkassa.offline_queue.domain.model.QueueLane
import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus
import kz.mybrain.superkassa.offline_queue.domain.port.LeaseLockPort
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort
import org.slf4j.LoggerFactory

/**
 * Сервис очереди команд (offline).
 */
class QueueService(
    private val storage: QueueStoragePort,
    private val lockPort: LeaseLockPort,
    private val handler: QueueCommandHandler,
    private val backoffPolicy: BackoffPolicy,
    private val ownerId: String,
    private val leaseMs: Long = 15000,
    private val timeProvider: TimeProvider = SystemTimeProvider
) {
    private val logger = LoggerFactory.getLogger(QueueService::class.java)

    fun enqueue(command: QueueCommand): Boolean {
        logger.info("Queue enqueue. cashboxId={}, lane={}, type={}, id={}", command.cashboxId, command.lane, command.type, command.id)
        return storage.enqueue(command)
    }

    fun hasOfflineQueue(cashboxId: String): Boolean {
        val offline = storage.listByCashbox(cashboxId, QueueLane.OFFLINE, 100)
        return offline.any { it.status != QueueStatus.SENT }
    }

    fun processNext(cashboxId: String, lane: QueueLane): Boolean {
        val now = timeProvider.now()
        val leaseUntil = now + leaseMs
        if (!lockPort.tryAcquire(cashboxId, ownerId, leaseUntil, now)) {
            logger.info("Queue lock busy. cashboxId={}, ownerId={}", cashboxId, ownerId)
            return false
        }
        try {
            val next = storage.nextPending(cashboxId, lane, now) ?: return false
            storage.markInProgress(next.id, now)
            val result = handler.handle(next)
            applyResult(next, result, now)
            return true
        } finally {
            lockPort.release(cashboxId, ownerId)
        }
    }

    fun processBatch(cashboxId: String, lane: QueueLane, limit: Int): Int {
        var processed = 0
        while (processed < limit) {
            val ok = processNext(cashboxId, lane)
            if (!ok) break
            processed++
        }
        return processed
    }

    private fun applyResult(command: QueueCommand, result: DispatchResult, now: Long) {
        val attempt = command.attempt + 1
        when (result.status) {
            QueueStatus.SENT -> {
                storage.updateStatus(command.id, QueueStatus.SENT, attempt, null, null)
                logger.info("Queue command sent. id={}", command.id)
            }
            QueueStatus.FAILED -> {
                val retryAt = result.retryAt ?: backoffPolicy.nextAttemptAt(now, attempt)
                storage.updateStatus(command.id, QueueStatus.FAILED, attempt, result.errorMessage, retryAt)
                logger.warn("Queue command failed. id={}, retryAt={}", command.id, retryAt)
            }
            QueueStatus.PENDING, QueueStatus.IN_PROGRESS -> {
                storage.updateStatus(command.id, result.status, attempt, result.errorMessage, result.retryAt)
                logger.info("Queue command status updated. id={}, status={}", command.id, result.status)
            }
        }
    }
}
