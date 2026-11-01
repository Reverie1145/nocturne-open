package cn.reverie.render.ui.notification
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.render.NotificationModule
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.hud.HUDModule
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

object NotificationManager {

    private val toasts = CopyOnWriteArrayList<ToastNotification>()

    val activeToasts: List<ToastNotification>
        get() = toasts.filter { !it.isExpired }

    val currentAlert: ToastNotification?
        get() = toasts.lastOrNull { !it.isExpired && !it.isDismissed }

    private var wasMouseDown = false
    private var draggedToast: ToastNotification? = null
    private var dragStartX = 0f

    fun show(
        title: String,
        message: String,
        type: ToastType = ToastType.INFO,
        durationMs: Long = 3000L,
        category: Category? = null
    ) {

        val existing = toasts.firstOrNull {
            !it.isExpired && !it.isDismissed && it.title.equals(title, ignoreCase = true)
        }
        if (existing != null) {
            existing.onCoalesce(message)
            NotificationSoundHelper.play(type)
            return
        }

        val maxVisible = if (NotificationModule.enabled) NotificationModule.maxStack.getInt() else 5
        val activeCount = toasts.count { !it.isExpired && !it.isDismissed }
        if (activeCount >= maxVisible) {
            toasts.firstOrNull { !it.isExpired && !it.isDismissed }?.dismiss()
        }

        val customDuration = if (NotificationModule.enabled) {
            (NotificationModule.duration.getDouble() * 1000.0).toLong().coerceAtLeast(1200L)
        } else durationMs

        val toast = ToastNotification(title, message, type, customDuration, category)
        toasts.add(toast)
        NotificationSoundHelper.play(type)
    }

    fun success(title: String, message: String) = show(title, message, ToastType.SUCCESS)
    fun error(title: String, message: String) = show(title, message, ToastType.ERROR)
    fun warning(title: String, message: String) = show(title, message, ToastType.WARNING)
    fun info(title: String, message: String) = show(title, message, ToastType.INFO)

    fun onModuleToggle(module: Module) {
        cn.reverie.render.hud.impl.DynamicIslandWidget.onModuleToggle(module)

        if (!NotificationModule.enabled || !NotificationModule.notifyOnToggle.value) return
        if (module == NotificationModule) return

        val type = if (module.enabled) ToastType.MODULE_ON else ToastType.MODULE_OFF
        val stateText = if (module.enabled) "Enabled" else "Disabled"
        val keyName = if (module.keyCode != GLFW.GLFW_KEY_UNKNOWN) {
            val name = cn.reverie.util.misc.KeyUtil.getKeyName(module.keyCode)
            " [$name]"
        } else ""

        show(
            title = module.name,
            message = "$stateText$keyName",
            type = type,
            category = module.category
        )
    }

    fun clearAll() {
        for (toast in toasts) {
            toast.dismiss()
        }
    }

    fun render() {
        if (!RenderEngine.isInitialized) return

        for (toast in toasts) {
            toast.tickLifetime()
        }
        toasts.removeIf { it.isExpired }
        if (toasts.isEmpty()) return

        val mc = Minecraft.getInstance()
        val window = mc.window
        val isScreenOpen = mc.gui.screen() != null
        val mouseX = if (isScreenOpen) mc.mouseHandler.getScaledXPos(window).toFloat() else -9999f
        val mouseY = if (isScreenOpen) mc.mouseHandler.getScaledYPos(window).toFloat() else -9999f
        val isMouseDown = isScreenOpen && GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS

        val justPressed = isMouseDown && !wasMouseDown
        wasMouseDown = isMouseDown

        HUDModule.withHudFont {
            for (toast in toasts) {
                val hovered = isScreenOpen && toast.hitTest(mouseX, mouseY)
                toast.updateHoverState(hovered)

                if (justPressed && hovered) {
                    if (toast.isCloseButtonHit(mouseX, mouseY)) {
                        toast.dismiss()
                    } else {
                        draggedToast = toast
                        dragStartX = mouseX
                    }
                }
            }
        }

        val currentDrag = draggedToast
        if (currentDrag != null) {
            if (isMouseDown) {
                currentDrag.dragOffsetX = mouseX - dragStartX
            } else {
                if (abs(currentDrag.dragOffsetX) > 55f) {
                    currentDrag.dismiss()
                } else {
                    currentDrag.dragOffsetX = 0f
                }
                draggedToast = null
            }
        }
    }
}
