package kz.mybrain.superkassa.offline_queue.domain.model

/**
 * Тип команды, которая будет отправляться в ОФД.
 */
enum class QueueCommandType {
    TICKET,
    REPORT_X,
    REPORT_Z,
    CLOSE_SHIFT,
    MONEY_PLACEMENT,
    INFO,
    SYSTEM
}
