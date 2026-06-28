package kz.mybrain.superkassa.offline_queue.application.logging

import kotlin.reflect.KClass

expect class Logger {
    fun info(message: String)
    fun info(message: String, vararg args: Any?)
    fun warn(message: String, vararg args: Any?)
    fun error(message: String, throwable: Throwable?)
}

expect fun getLogger(clazz: KClass<*>): Logger
