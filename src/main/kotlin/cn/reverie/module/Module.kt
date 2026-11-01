package cn.reverie.module

import cn.reverie.app.config.ConfigManager
import cn.reverie.app.event.EventBus
import cn.reverie.app.event.EventSubscription
import org.lwjgl.glfw.GLFW
import cn.reverie.render.ui.notification.NotificationManager
abstract class Module(
    val name: String,
    val description: String,
    val category: Category,
    keyCode: Int = GLFW.GLFW_KEY_UNKNOWN
) {

    var keyCode: Int = keyCode
        set(value) {
            if (field == value) return
            field = value
            ConfigManager.markDirty()
        }

    var enabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                activateSubscriptions()
                try {
                    onEnable()
                } catch (failure: Throwable) {
                    deactivateSubscriptions()
                    throw failure
                }
            } else {
                try {
                    onDisable()
                } finally {
                    deactivateSubscriptions()
                }
            }
            onToggle()
            ConfigManager.markDirty()
            NotificationManager.onModuleToggle(this)
        }

    val hidden = BooleanSetting("Hidden", "Hide from ArrayList", false)

    private var settingsInitialized = false
    private val _settings = mutableListOf<Setting<*>>()
    private val subscriptions = mutableListOf<EventSubscription>()
    private val subscriptionFactories = mutableListOf<() -> EventSubscription>()

    val settings: List<Setting<*>>
        get() {
            if (!settingsInitialized) {
                registerSettings()
                settingsInitialized = true
            }
            return _settings
        }

    private fun registerSettings() {
        val manualSettings = ArrayList(_settings)
        _settings.clear()
        var clazz: Class<*>? = this.javaClass
        while (clazz != null && clazz != Module::class.java) {
            for (field in clazz.declaredFields) {
                try {
                    field.isAccessible = true
                    val obj = field.get(this)
                    if (obj is Setting<*> && obj != hidden && !_settings.contains(obj)) {
                        _settings.add(obj)
                    }
                } catch (ignored: Exception) {
                }
            }
            clazz = clazz.superclass
        }
        for (manual in manualSettings) {
            if (!_settings.contains(manual) && manual != hidden) {
                _settings.add(manual)
            }
        }
        if (!_settings.contains(hidden)) {
            _settings.add(hidden)
        }
    }

    fun addSetting(setting: Setting<*>) {
        if (!_settings.contains(setting)) {
            _settings.add(setting)
        }
    }

    /** Keeps module event listeners active only for the enabled lifetime. */
    protected fun <T : Any> listen(eventType: Class<T>, listener: (T) -> Unit) {
        val factory = { EventBus.subscribe(eventType, listener) }
        subscriptionFactories += factory
        if (enabled) subscriptions += factory()
    }

    private fun activateSubscriptions() {
        if (subscriptions.isNotEmpty()) return
        subscriptionFactories.forEach { subscriptions += it() }
    }

    private fun deactivateSubscriptions() {
        subscriptions.toList().forEach(EventSubscription::close)
        subscriptions.clear()
    }

    fun toggle() {
        enabled = !enabled
    }

    open fun onEnable() {}
    open fun onDisable() {}
    open fun onToggle() {}

    open val suffix: String?
        get() {
            val modeSetting = settings.firstOrNull { it is ModeSetting } as? ModeSetting
            return modeSetting?.value
        }
}
