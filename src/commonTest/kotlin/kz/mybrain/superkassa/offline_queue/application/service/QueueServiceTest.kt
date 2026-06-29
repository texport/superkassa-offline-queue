package kz.mybrain.superkassa.offline_queue.application.service

import kz.mybrain.superkassa.offline_queue.application.model.DispatchResult
import kz.mybrain.superkassa.offline_queue.application.policy.BackoffPolicy
import kz.mybrain.superkassa.offline_queue.application.policy.TimeProvider
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommand
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommandType
import kz.mybrain.superkassa.offline_queue.domain.model.QueueLane
import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus
import kz.mybrain.superkassa.offline_queue.domain.port.LeaseLockPort
import kz.mybrain.superkassa.offline_queue.domain.port.QueueStoragePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueServiceTest {

    private class StubStorage : QueueStoragePort {
        val commands = mutableListOf<QueueCommand>()
        var nextPendingResponse: QueueCommand? = null
        var lastStatusUpdate: StatusUpdate? = null
        var lastInProgressId: String? = null

        data class StatusUpdate(val id: String, val status: QueueStatus, val attempt: Int, val lastError: String?, val nextAttemptAt: Long?)

        override fun enqueue(command: QueueCommand): Boolean {
            commands.add(command)
            return true
        }

        override fun nextPending(cashboxId: String, lane: QueueLane, now: Long): QueueCommand? = nextPendingResponse

        override fun updateStatus(id: String, status: QueueStatus, attempt: Int, lastError: String?, nextAttemptAt: Long?): Boolean {
            lastStatusUpdate = StatusUpdate(id, status, attempt, lastError, nextAttemptAt)
            return true
        }

        override fun markInProgress(id: String, now: Long): Boolean {
            lastInProgressId = id
            return true
        }

        override fun listByCashbox(cashboxId: String, lane: QueueLane, limit: Int, offset: Int): List<QueueCommand> {
            return commands.filter { it.cashboxId == cashboxId && it.lane == lane }
        }

        override fun deleteByCashbox(cashboxId: String): Boolean {
            commands.removeAll { it.cashboxId == cashboxId }
            return true
        }
    }

    private class StubLock : LeaseLockPort {
        var lockAcquired = false
        var lockBusy = false
        var lastAcquireParams: AcquireParams? = null
        var releasedId: String? = null

        data class AcquireParams(val cashboxId: String, val ownerId: String, val leaseUntil: Long, val now: Long)

        override fun tryAcquire(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean {
            if (lockBusy) return false
            lockAcquired = true
            lastAcquireParams = AcquireParams(cashboxId, ownerId, leaseUntil, now)
            return true
        }

        override fun renew(cashboxId: String, ownerId: String, leaseUntil: Long, now: Long): Boolean = true

        override fun release(cashboxId: String, ownerId: String): Boolean {
            lockAcquired = false
            releasedId = cashboxId
            return true
        }
    }

    @Test
    fun testEnqueue() {
        val storage = StubStorage()
        val service = QueueService(
            storage = storage,
            lockPort = StubLock(),
            handler = { DispatchResult(QueueStatus.SENT) },
            backoffPolicy = { _, _ -> 0L },
            ownerId = "node-1"
        )
        val cmd = QueueCommand(
            "1",
            "c1",
            QueueLane.OFFLINE,
            QueueCommandType.TICKET,
            "ref1",
            1000L,
            QueueStatus.PENDING,
            0
        )
        assertTrue(service.enqueue(cmd))
        assertEquals(1, storage.commands.size)
        assertEquals("1", storage.commands[0].id)
    }

    @Test
    fun testHasOfflineQueue() {
        val storage = StubStorage()
        val service = QueueService(
            storage = storage,
            lockPort = StubLock(),
            handler = { DispatchResult(QueueStatus.SENT) },
            backoffPolicy = { _, _ -> 0L },
            ownerId = "node-1"
        )
        val cmd = QueueCommand(
            "1",
            "c1",
            QueueLane.OFFLINE,
            QueueCommandType.TICKET,
            "ref1",
            1000L,
            QueueStatus.PENDING,
            0
        )
        storage.commands.add(cmd)
        assertTrue(service.hasOfflineQueue("c1"))

        storage.commands.clear()
        val sentCmd = QueueCommand(
            "2",
            "c1",
            QueueLane.OFFLINE,
            QueueCommandType.TICKET,
            "ref1",
            1000L,
            QueueStatus.SENT,
            0
        )
        storage.commands.add(sentCmd)
        assertFalse(service.hasOfflineQueue("c1"))
    }

    @Test
    fun testProcessNextSuccess() {
        val storage = StubStorage()
        val lock = StubLock()
        var handledCommand: QueueCommand? = null
        val handler = QueueCommandHandler { cmd ->
            handledCommand = cmd
            DispatchResult(QueueStatus.SENT)
        }
        val timeProvider = TimeProvider { 5000L }
        val service = QueueService(
            storage = storage,
            lockPort = lock,
            handler = handler,
            backoffPolicy = { _, _ -> 0L },
            ownerId = "node-1",
            leaseMs = 10000,
            timeProvider = timeProvider
        )

        val cmd = QueueCommand(
            "1",
            "c1",
            QueueLane.OFFLINE,
            QueueCommandType.TICKET,
            "ref1",
            1000L,
            QueueStatus.PENDING,
            0
        )
        storage.nextPendingResponse = cmd

        assertTrue(service.processNext("c1", QueueLane.OFFLINE))

        // Check lock was acquired and released
        assertTrue(lock.lockAcquired.not())
        assertEquals("c1", lock.releasedId)
        assertEquals(StubLock.AcquireParams("c1", "node-1", 15000L, 5000L), lock.lastAcquireParams)

        // Check handler processed correct command
        assertEquals("1", handledCommand?.id)

        // Check status updated
        assertEquals("1", storage.lastInProgressId)
        assertEquals(StubStorage.StatusUpdate("1", QueueStatus.SENT, 1, null, null), storage.lastStatusUpdate)
    }

    @Test
    fun testProcessNextLockBusy() {
        val storage = StubStorage()
        val lock = StubLock().apply { lockBusy = true }
        val service = QueueService(
            storage = storage,
            lockPort = lock,
            handler = { DispatchResult(QueueStatus.SENT) },
            backoffPolicy = { _, _ -> 0L },
            ownerId = "node-1"
        )
        assertFalse(service.processNext("c1", QueueLane.OFFLINE))
    }

    @Test
    fun testProcessNextFailureWithBackoff() {
        val storage = StubStorage()
        val lock = StubLock()
        val backoff = BackoffPolicy { now, attempt -> now + attempt * 2000L }
        val service = QueueService(
            storage = storage,
            lockPort = lock,
            handler = { DispatchResult(QueueStatus.FAILED, errorMessage = "OFD Timeout") },
            backoffPolicy = backoff,
            ownerId = "node-1",
            timeProvider = { 10000L }
        )

        val cmd = QueueCommand(
            "1",
            "c1",
            QueueLane.OFFLINE,
            QueueCommandType.TICKET,
            "ref1",
            1000L,
            QueueStatus.PENDING,
            1
        )
        storage.nextPendingResponse = cmd

        assertTrue(service.processNext("c1", QueueLane.OFFLINE))

        // Check failure status update with correct attempt and retryTime
        assertEquals(
            StubStorage.StatusUpdate("1", QueueStatus.FAILED, 2, "OFD Timeout", 14000L),
            storage.lastStatusUpdate
        )
    }

    @Test
    fun testProcessNextHandlerThrowsException() {
        val storage = StubStorage()
        val lock = StubLock()
        val backoff = BackoffPolicy { now, attempt -> now + attempt * 2000L }
        val service = QueueService(
            storage = storage,
            lockPort = lock,
            handler = { throw IllegalStateException("Database Connection Lost") },
            backoffPolicy = backoff,
            ownerId = "node-1",
            timeProvider = { 10000L }
        )

        val cmd = QueueCommand(
            "1",
            "c1",
            QueueLane.OFFLINE,
            QueueCommandType.TICKET,
            "ref1",
            1000L,
            QueueStatus.PENDING,
            0
        )
        storage.nextPendingResponse = cmd

        assertTrue(service.processNext("c1", QueueLane.OFFLINE))

        // Lock should be released successfully
        assertTrue(lock.lockAcquired.not())

        // The command status should be updated to FAILED with the exception message
        assertEquals(
            StubStorage.StatusUpdate(
                "1",
                QueueStatus.FAILED,
                1,
                "Database Connection Lost",
                12000L
            ),
            storage.lastStatusUpdate
        )
    }

    @Test
    fun testDefaultBackoffPolicy() {
        val policy = kz.mybrain.superkassa.offline_queue.application.policy.DefaultBackoffPolicy(1000, 60000)
        assertEquals(11000L, policy.nextAttemptAt(10000L, 0)) // 10000 + 1000 * 2^0 = 11000
        assertEquals(12000L, policy.nextAttemptAt(10000L, 1)) // 10000 + 1000 * 2^1 = 12000
        assertEquals(14000L, policy.nextAttemptAt(10000L, 2)) // 10000 + 1000 * 2^2 = 14000
        assertEquals(70000L, policy.nextAttemptAt(10000L, 10)) // 10000 + 60000 (max) = 70000

        val defaultPolicy = kz.mybrain.superkassa.offline_queue.application.policy.DefaultBackoffPolicy()
        assertEquals(11000L, defaultPolicy.nextAttemptAt(10000L, 0))
    }

    @Test
    fun testSystemTimeProvider() {
        val now = kz.mybrain.superkassa.offline_queue.application.policy.SystemTimeProvider.now()
        assertTrue(now > 0)
    }

    @Test
    fun testProcessNextNoPending() {
        val storage = StubStorage()
        val lock = StubLock()
        val service = QueueService(
            storage = storage,
            lockPort = lock,
            handler = { DispatchResult(QueueStatus.SENT) },
            backoffPolicy = { _, _ -> 0L },
            ownerId = "node-1"
        )
        storage.nextPendingResponse = null
        assertFalse(service.processNext("c1", QueueLane.OFFLINE))
        assertTrue(lock.lockAcquired.not()) // Released in finally
    }

    @Test
    fun testProcessBatch() {
        val storage = StubStorage()
        val lock = StubLock()
        var processCount = 0
        val service = QueueService(
            storage = storage,
            lockPort = lock,
            handler = {
                processCount++
                storage.nextPendingResponse = null
                DispatchResult(QueueStatus.SENT)
            },
            backoffPolicy = { _, _ -> 0L },
            ownerId = "node-1"
        )
        
        val cmd = QueueCommand("1", "c1", QueueLane.OFFLINE, QueueCommandType.TICKET, "ref1", 1000L, QueueStatus.PENDING, 0)
        storage.nextPendingResponse = cmd

        // First batch run processes 1
        assertEquals(1, service.processBatch("c1", QueueLane.OFFLINE, 10))
        assertEquals(1, processCount)

        // If lock acquisition fails, stops batch processing
        lock.lockBusy = true
        assertEquals(0, service.processBatch("c1", QueueLane.OFFLINE, 10))
    }

    @Test
    fun testApplyResultPendingAndInProgress() {
        val storage = StubStorage()
        val lock = StubLock()
        var currentStatus = QueueStatus.PENDING
        val service = QueueService(
            storage = storage,
            lockPort = lock,
            handler = { DispatchResult(currentStatus, retryAt = 15000L) },
            backoffPolicy = { _, _ -> 0L },
            ownerId = "node-1"
        )
        val cmd = QueueCommand("1", "c1", QueueLane.OFFLINE, QueueCommandType.TICKET, "ref1", 1000L, QueueStatus.PENDING, 0)
        storage.nextPendingResponse = cmd

        assertTrue(service.processNext("c1", QueueLane.OFFLINE))
        assertEquals(
            StubStorage.StatusUpdate("1", QueueStatus.PENDING, 1, null, 15000L),
            storage.lastStatusUpdate
        )

        currentStatus = QueueStatus.IN_PROGRESS
        storage.nextPendingResponse = cmd.copy(attempt = 1)
        assertTrue(service.processNext("c1", QueueLane.OFFLINE))
        assertEquals(
            StubStorage.StatusUpdate("1", QueueStatus.IN_PROGRESS, 2, null, 15000L),
            storage.lastStatusUpdate
        )
    }
}
