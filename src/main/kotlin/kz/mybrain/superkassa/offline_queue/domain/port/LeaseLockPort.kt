package kz.mybrain.superkassa.offline_queue.domain.port

/**
 * Порт блокировки по кассе для воркера очереди.
 */
interface LeaseLockPort {
    fun tryAcquire(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean
    fun renew(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean
    fun release(cashboxId: String, ownerId: String): Boolean
}
