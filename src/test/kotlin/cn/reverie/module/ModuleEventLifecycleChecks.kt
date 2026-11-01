package cn.reverie.module

import cn.reverie.app.event.EventBus
import cn.reverie.app.event.EventSubscription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleEventLifecycleChecks {
    @Test
    fun subscriptionsFollowEnableLifetimeAndAreNotDuplicated() {
        val event = CancellableTestEvent()
        val module = TestListenerModule()

        EventBus.post(event)
        assertEquals(0, module.calls)

        module.enabled = true
        EventBus.post(event)
        assertEquals(1, module.calls)
        assertTrue(event.cancelled)

        module.enabled = true
        EventBus.post(event)
        assertEquals(2, module.calls, "repeated enable must not add another listener")

        module.enabled = false
        EventBus.post(event)
        assertEquals(2, module.calls, "disable must release listeners")

        module.enabled = true
        EventBus.post(event)
        assertEquals(3, module.calls, "reenable must create exactly one fresh listener")
        module.enabled = false
    }

    @Test
    fun subscriptionCloseIsIdempotent() {
        var calls = 0
        val subscription: EventSubscription = EventBus.register<CancellableTestEvent> { calls++ }
        EventBus.post(CancellableTestEvent())
        assertEquals(1, calls)

        subscription.close()
        subscription.close()
        assertTrue(subscription.isClosed)
        EventBus.post(CancellableTestEvent())
        assertEquals(1, calls)
    }
}

private class CancellableTestEvent(var cancelled: Boolean = false)

private class TestListenerModule : Module("test-listener", "event lifecycle test", Category.MISC) {
    var calls = 0

    init {
        listen(CancellableTestEvent::class.java) {
            calls++
            it.cancelled = true
        }
    }
}
