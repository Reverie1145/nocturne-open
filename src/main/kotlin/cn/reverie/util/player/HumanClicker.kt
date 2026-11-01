package cn.reverie.util.player

import cn.reverie.mixin.gui.AbstractContainerScreenAccessor
import cn.reverie.mixin.input.MouseHandlerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.hypot
import kotlin.math.pow

object HumanClicker {

    private val mc = Minecraft.getInstance()

    enum class ActionType {
        SHIFT_CLICK,
        LEFT_CLICK,
        RIGHT_CLICK,
        DROP_KEY,
        HOTBAR_KEY
    }

    data class ClickTask(
        val slot: Slot,
        val actionType: ActionType,
        val hotbarNumber: Int = 0,
        val onCompleted: (() -> Unit)? = null
    )

    private enum class State {
        IDLE,
        MOVING,
        HOVERING,
        PRESSING,
        RELEASING
    }

    private var state = State.IDLE

    var curX: Double = 0.0
    var curY: Double = 0.0
    private var initializedPos = false

    private var startX: Double = 0.0
    private var startY: Double = 0.0
    private var targetX: Double = 0.0
    private var targetY: Double = 0.0
    private var ctrlX1: Double = 0.0
    private var ctrlY1: Double = 0.0
    private var ctrlX2: Double = 0.0
    private var ctrlY2: Double = 0.0

    private var moveStartTime: Long = 0L
    private var moveDuration: Long = 0L

    private var stateStartTime: Long = 0L
    private var hoverDelay: Long = 0L
    private var pressDuration: Long = 0L

    private var currentTask: ClickTask? = null

    var moveCursor: Boolean = true
    var syncRealMouse: Boolean = false
    var moveSpeed: Double = 2.0
    var baseHoldTime: Long = 20L

    val isBusy: Boolean
        get() = state != State.IDLE || currentTask != null

    fun reset() {
        if (state == State.PRESSING) {
            releaseInput()
        }
        state = State.IDLE
        currentTask = null
    }

    fun syncCursorFromMc() {
        val window = mc.window
        val mouseHandler = mc.mouseHandler
        curX = mouseHandler.getScaledXPos(window)
        curY = mouseHandler.getScaledYPos(window)
        initializedPos = true
    }

    fun schedule(
        task: ClickTask,
        enableCursorMove: Boolean = true,
        syncReal: Boolean = false,
        speed: Double = 2.0,
        holdMs: Long = 20L
    ): Boolean {
        if (isBusy) return false

        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return false
        val screenAccessor = screen as? AbstractContainerScreenAccessor ?: return false

        moveCursor = enableCursorMove
        syncRealMouse = syncReal
        moveSpeed = speed
        baseHoldTime = holdMs

        if (!initializedPos) {
            syncCursorFromMc()
        }

        currentTask = task

        val left = screenAccessor.nocturneGetLeftPos()
        val top = screenAccessor.nocturneGetTopPos()
        val rng = ThreadLocalRandom.current()
        val jitterX = rng.nextDouble(-1.5, 1.5)
        val jitterY = rng.nextDouble(-1.5, 1.5)

        targetX = left + task.slot.x + 8.0 + jitterX
        targetY = top + task.slot.y + 8.0 + jitterY

        if (!moveCursor) {
            curX = targetX
            curY = targetY
            updateCursorPosition(curX, curY)
            if (holdMs <= 0L) {

                pressInput(task)
                releaseInput()
                state = State.IDLE
                currentTask = null
                task.onCompleted?.invoke()
                return true
            }
            state = State.HOVERING
            stateStartTime = System.currentTimeMillis()
            hoverDelay = 0L
            return true
        }

        startX = curX
        startY = curY

        val dist = hypot(targetX - startX, targetY - startY)
        val baseMs = if (holdMs <= 0L) {
            (dist * 0.1 / moveSpeed).toLong().coerceIn(0L, 12L)
        } else {
            (dist * 0.25 / moveSpeed).toLong().coerceIn(4L, 25L)
        }
        moveDuration = baseMs
        moveStartTime = System.currentTimeMillis()

        val midX = (startX + targetX) / 2.0
        val midY = (startY + targetY) / 2.0
        val deviation = (dist * rng.nextDouble(0.02, 0.08)) * (if (rng.nextBoolean()) 1 else -1)

        ctrlX1 = startX + (midX - startX) * 0.5 - deviation * 0.2
        ctrlY1 = startY + (midY - startY) * 0.5 + deviation
        ctrlX2 = midX + (targetX - midX) * 0.5 + deviation * 0.2
        ctrlY2 = midY + (targetY - midY) * 0.5 - deviation * 0.3

        state = State.MOVING
        return true
    }

    fun onRenderFrame() {
        if (state != State.MOVING) return

        val screen = mc.gui.screen() as? AbstractContainerScreen<*>
        if (screen == null) {
            reset()
            return
        }

        val elapsed = System.currentTimeMillis() - moveStartTime
        val t = if (moveDuration <= 0L) 1.0 else (elapsed.toDouble() / moveDuration.toDouble()).coerceIn(0.0, 1.0)

        val easeT = easeOutCubic(t)
        val pos = bezierPoint(startX, startY, ctrlX1, ctrlY1, ctrlX2, ctrlY2, targetX, targetY, easeT)

        curX = pos.first
        curY = pos.second
        updateCursorPosition(curX, curY)

        if (t >= 1.0) {
            curX = targetX
            curY = targetY
            updateCursorPosition(curX, curY)
            val task = currentTask
            if (baseHoldTime <= 0L && task != null) {

                pressInput(task)
                releaseInput()
                state = State.IDLE
                currentTask = null
                task.onCompleted?.invoke()
            } else {
                state = State.HOVERING
                stateStartTime = System.currentTimeMillis()
                hoverDelay = if (baseHoldTime <= 0L) 0L else ThreadLocalRandom.current().nextLong(2, 6)
            }
        }
    }

    fun onTick() {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*>
        if (screen == null) {
            reset()
            return
        }

        val now = System.currentTimeMillis()

        when (state) {
            State.IDLE -> {}

            State.MOVING -> {
                if (now - moveStartTime >= moveDuration + 10L) {
                    curX = targetX
                    curY = targetY
                    updateCursorPosition(curX, curY)
                    val task = currentTask
                    if (baseHoldTime <= 0L && task != null) {
                        pressInput(task)
                        releaseInput()
                        state = State.IDLE
                        currentTask = null
                        task.onCompleted?.invoke()
                    } else {
                        state = State.HOVERING
                        stateStartTime = now
                        hoverDelay = if (baseHoldTime <= 0L) 0L else ThreadLocalRandom.current().nextLong(2, 6)
                    }
                }
            }

            State.HOVERING -> {
                if (now - stateStartTime >= hoverDelay) {
                    val task = currentTask
                    if (task == null) {
                        state = State.IDLE
                        return
                    }
                    pressInput(task)
                    if (baseHoldTime <= 0L) {
                        releaseInput()
                        state = State.IDLE
                        currentTask = null
                        task.onCompleted?.invoke()
                        return
                    }
                    state = State.PRESSING
                    stateStartTime = now
                    pressDuration = baseHoldTime
                }
            }

            State.PRESSING -> {
                if (now - stateStartTime >= pressDuration) {
                    releaseInput()
                    val task = currentTask
                    state = State.IDLE
                    currentTask = null
                    task?.onCompleted?.invoke()
                }
            }

            State.RELEASING -> {
                val task = currentTask
                state = State.IDLE
                currentTask = null
                task?.onCompleted?.invoke()
            }
        }
    }

    private fun updateCursorPosition(scaledX: Double, scaledY: Double) {
        val window = mc.window
        val scaledWidth = window.guiScaledWidth.toDouble()
        val scaledHeight = window.guiScaledHeight.toDouble()
        if (scaledWidth <= 0.0 || scaledHeight <= 0.0) return

        val screenWidth = window.width.toDouble()
        val screenHeight = window.height.toDouble()

        val absX = scaledX * screenWidth / scaledWidth
        val absY = scaledY * screenHeight / scaledHeight

        val mouseHandler = mc.mouseHandler as? MouseHandlerAccessor
        if (mouseHandler != null) {
            mouseHandler.nocturneSetXpos(absX)
            mouseHandler.nocturneSetYpos(absY)
        }

        val screen = mc.gui.screen()
        if (screen is AbstractContainerScreen<*>) {
            val screenAccessor = screen as? AbstractContainerScreenAccessor
            if (screenAccessor != null) {
                val hovered = screenAccessor.nocturneInvokeGetHoveredSlot(scaledX, scaledY)
                screenAccessor.nocturneSetHoveredSlot(hovered)
            }
            screen.mouseMoved(scaledX, scaledY)
        }

        if (syncRealMouse && mc.isWindowActive) {
            GLFW.glfwSetCursorPos(window.handle(), absX, absY)
        }
    }

    private fun pressInput(task: ClickTask) {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        val screenAccessor = screen as? AbstractContainerScreenAccessor

        screenAccessor?.nocturneSetHoveredSlot(task.slot)

        when (task.actionType) {
            ActionType.SHIFT_CLICK -> {

                screenAccessor?.nocturneInvokeSlotClicked(task.slot, task.slot.index, 0, ContainerInput.QUICK_MOVE)
            }

            ActionType.LEFT_CLICK -> {
                screenAccessor?.nocturneInvokeSlotClicked(task.slot, task.slot.index, 0, ContainerInput.PICKUP)
            }

            ActionType.RIGHT_CLICK -> {
                screenAccessor?.nocturneInvokeSlotClicked(task.slot, task.slot.index, 1, ContainerInput.PICKUP)
            }

            ActionType.DROP_KEY -> {

                screenAccessor?.nocturneInvokeSlotClicked(task.slot, task.slot.index, 1, ContainerInput.THROW)
            }

            ActionType.HOTBAR_KEY -> {

                val hotbarIdx = task.hotbarNumber.coerceIn(0, 8)
                screenAccessor?.nocturneInvokeSlotClicked(task.slot, task.slot.index, hotbarIdx, ContainerInput.SWAP)
            }
        }
    }

    private fun releaseInput() {

    }

    private fun easeOutCubic(x: Double): Double {
        return 1.0 - (1.0 - x).pow(3.0)
    }

    private fun bezierPoint(
        p0x: Double, p0y: Double,
        p1x: Double, p1y: Double,
        p2x: Double, p2y: Double,
        p3x: Double, p3y: Double,
        t: Double
    ): Pair<Double, Double> {
        val u = 1.0 - t
        val tt = t * t
        val uu = u * u
        val uuu = uu * u
        val ttt = tt * t

        val x = uuu * p0x + 3 * uu * t * p1x + 3 * u * tt * p2x + ttt * p3x
        val y = uuu * p0y + 3 * uu * t * p1y + 3 * u * tt * p2y + ttt * p3y
        return Pair(x, y)
    }
}
