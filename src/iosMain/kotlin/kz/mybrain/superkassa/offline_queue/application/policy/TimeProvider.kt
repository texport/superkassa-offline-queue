package kz.mybrain.superkassa.offline_queue.application.policy

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.gettimeofday
import platform.posix.timeval

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
    memScoped {
        val tv = alloc<timeval>()
        gettimeofday(tv.ptr, null)
        return tv.tv_sec * 1000L + tv.tv_usec / 1000L
    }
}
