package kz.mybrain.superkassa.offline_queue.application.logging

import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

actual class Logger(private val delegate: org.slf4j.Logger) {
    actual fun info(message: String) {
        delegate.info(message)
    }
    actual fun info(message: String, vararg args: Any?) {
        delegate.info(message, *args)
    }
    actual fun warn(message: String, vararg args: Any?) {
        delegate.warn(message, *args)
    }
    actual fun error(message: String, throwable: Throwable?) {
        delegate.error(message, throwable)
    }
}

actual fun getLogger(clazz: KClass<*>): Logger {
    return Logger(LoggerFactory.getLogger(clazz.java))
}
