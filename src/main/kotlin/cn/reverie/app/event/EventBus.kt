package cn.reverie.app.event

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
interface EventSubscription : AutoCloseable {
    val isClosed: Boolean
    override fun close()
}

private class BusSubscription(private val dispose: () -> Unit) : EventSubscription {
    private val closed = AtomicBoolean(false)

    override val isClosed: Boolean
        get() = closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) dispose()
    }
}

object EventBus {

    @PublishedApi
    internal val listeners = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<(Any) -> Unit>>()

    inline fun <reified T : Any> register(noinline listener: (T) -> Unit): EventSubscription =
        subscribe(T::class.java, listener)

    fun <T : Any> subscribe(eventType: Class<T>, listener: (T) -> Unit): EventSubscription {
        val list = listeners.computeIfAbsent(eventType) { CopyOnWriteArrayList() }
        @Suppress("UNCHECKED_CAST")
        val callback = listener as (Any) -> Unit
        list.add(callback)
        return BusSubscription { list.remove(callback) }
    }

    inline fun <reified T : Any> unregister(noinline listener: (T) -> Unit) {
        val list = listeners[T::class.java] ?: return
        @Suppress("UNCHECKED_CAST")
        list.remove(listener as (Any) -> Unit)
    }

    fun post(event: Any) {
        val list = listeners[event.javaClass]
        if (list != null) {
            for (listener in list) {
                listener(event)
            }
        }
    }
}
