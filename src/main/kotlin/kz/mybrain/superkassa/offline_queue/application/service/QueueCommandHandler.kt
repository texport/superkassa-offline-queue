package kz.mybrain.superkassa.offline_queue.application.service

import kz.mybrain.superkassa.offline_queue.application.model.DispatchResult
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommand

/**
 * Обработчик команд очереди (интеграция с ofd-manager).
 */
fun interface QueueCommandHandler {
    fun handle(command: QueueCommand): DispatchResult
}
