package kz.mybrain.superkassa.offline_queue.application.policy

/**
 * Поставщик времени для тестов и контроля.
 */
fun interface TimeProvider {
    fun now(): Long
}

expect fun currentTimeMillis(): Long

object SystemTimeProvider : TimeProvider {
    override fun now(): Long = currentTimeMillis()
}
