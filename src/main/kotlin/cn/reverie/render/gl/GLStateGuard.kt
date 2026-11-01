package cn.reverie.render.gl

import java.util.ArrayDeque

class GLStateGuard private constructor(private val tracker: GLStateTracker) : AutoCloseable {

    override fun close() {
        try {
            tracker.restore()
        } finally {
            recycleTracker(tracker)
        }
    }

    companion object {
        private val trackerPool = ThreadLocal.withInitial { ArrayDeque<GLStateTracker>() }

        @PublishedApi
        internal fun obtainTracker(): GLStateTracker {
            val pool = trackerPool.get()
            val tracker = if (pool.isNotEmpty()) pool.pop() else GLStateTracker()
            tracker.save()
            return tracker
        }

        @PublishedApi
        internal fun recycleTracker(tracker: GLStateTracker) {
            val pool = trackerPool.get()
            if (pool.size < 16) {
                pool.push(tracker)
            }
        }

        inline fun <R> withStateGuard(block: () -> R): R {
            val tracker = obtainTracker()
            return try {
                block()
            } finally {
                try {
                    tracker.restore()
                } finally {
                    recycleTracker(tracker)
                }
            }
        }

        fun push(): GLStateGuard {
            val tracker = obtainTracker()
            return GLStateGuard(tracker)
        }
    }
}
