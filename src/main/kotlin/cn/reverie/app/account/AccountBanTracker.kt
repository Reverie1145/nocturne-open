package cn.reverie.app.account

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import java.lang.reflect.Field

/**
 * Ban tracking ported from OpenMyau's Events class.
 *
 *  * Disconnect screens that say the account is (temporarily) banned or blocked store an unban time on the account
 *    that is currently signed in; the Accounts screen shows it as a countdown.
 *  * Joining a Hypixel server clears the mark again.
 */
object AccountBanTracker {

    private var handledScreen: DisconnectedScreen? = null
    private var lastLevel: ClientLevel? = null

    private val PERMANENT = listOf(
        "You are permanently banned from this server!",
        "Your account has been blocked."
    )
    private val TEMPORARY = listOf(
        Regex("You are temporarily banned for (.+?) from this server!"),
        Regex("Your account is temporarily blocked for (.+?) from this server!")
    )

    private var textFields: List<Field>? = null

    fun onTick() {
        val mc = Minecraft.getInstance()

        val level = mc.level
        if (level != null) {
            if (level !== lastLevel) {
                lastLevel = level
                val ip = mc.currentServer?.ip?.lowercase()
                val current = AccountManager.currentAccount
                if (ip != null && current != null && (ip.endsWith("hypixel.net") || ip.endsWith("hypixel.io"))) {
                    AccountManager.markUnban(current.name, 0L)
                }
            }
            return
        }
        lastLevel = null

        val screen = mc.gui.screen() as? DisconnectedScreen
        if (screen == null) {
            handledScreen = null
            return
        }
        if (screen === handledScreen) return
        handledScreen = screen

        val current = AccountManager.currentAccount ?: return
        if (current.type == AccountType.OFFLINE) return
        val text = readReason(screen)?.split("\n\n")?.firstOrNull()?.trim() ?: return

        if (PERMANENT.any { text == it }) {
            AccountManager.markUnban(current.name, -1L)
            return
        }
        for (pattern in TEMPORARY) {
            val duration = pattern.matchEntire(text)?.groupValues?.get(1) ?: continue
            parseDuration(duration)?.let { AccountManager.markUnban(current.name, System.currentTimeMillis() + it) }
            return
        }
    }

    /** "1d 2h 3m 4s" -> milliseconds. */
    private fun parseDuration(raw: String): Long? {
        var total = 0L
        var any = false
        for (part in raw.trim().split(' ')) {
            if (part.length < 2) continue
            val value = part.dropLast(1).toLongOrNull() ?: continue
            val unit = when (part.last()) {
                'd' -> 86_400_000L
                'h' -> 3_600_000L
                'm' -> 60_000L
                's' -> 1_000L
                else -> continue
            }
            total += value * unit
            any = true
        }
        return if (any) total else null
    }

    /**
     * The disconnect reason has no public accessor, so the screen's fields are scanned for a [Component] or for an
     * object with a `reason()` accessor (DisconnectionDetails).
     */
    private fun readReason(screen: DisconnectedScreen): String? {
        try {
            val fields = textFields ?: collectFields(screen.javaClass).also { textFields = it }
            for (field in fields) {
                val value = field.get(screen) ?: continue
                if (value is Component) return value.string
                val accessor = value.javaClass.methods.firstOrNull { it.name == "reason" && it.parameterCount == 0 } ?: continue
                (accessor.invoke(value) as? Component)?.let { return it.string }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun collectFields(start: Class<*>): List<Field> {
        val out = ArrayList<Field>()
        var cls: Class<*>? = start
        while (cls != null && cls != Any::class.java) {
            for (f in cls.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                val name = f.type.name
                if (Component::class.java.isAssignableFrom(f.type) || name.endsWith("DisconnectionDetails")) {
                    try {
                        f.isAccessible = true
                        out.add(f)
                    } catch (_: Throwable) {
                    }
                }
            }
            cls = cls.superclass
        }
        return out
    }
}
