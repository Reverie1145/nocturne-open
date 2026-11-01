package cn.reverie.module.render

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.render.ui.notification.NotificationManager
object NotificationModule : Module(
    name = "Notifications",
    description = "Sleek fluid notification & alert system",
    category = Category.RENDER
) {

    val notifyOnToggle = BooleanSetting("Notify On Toggle", "Show notification when toggling modules", true)

    val sound = ModeSetting(
        "Sound",
        "Audio haptic feedback sound",
        "ModernChime",
        listOf("ModernChime", "SoftClick", "None")
    )


    val duration = NumberSetting("Duration", "Toast display duration in seconds", 3.0, 1.5, 6.0, 0.5)

    val maxStack = NumberSetting("Max Stack", "Maximum concurrent visible notifications", 5.0, 1.0, 8.0, 1.0)

    val animSpeed = NumberSetting("Anim Speed", "Motion and spring physics speed multiplier", 1.0, 0.5, 2.0, 0.1)

    val autoWidth = BooleanSetting("Auto Width", "Dynamically adjust notification length based on content", true)

    val testPreview = BooleanSetting("Test Notification", "Trigger sample notifications to preview animations", false)

    init {
        enabled = true

        addSetting(notifyOnToggle)
        addSetting(sound)
        addSetting(duration)
        addSetting(maxStack)
        addSetting(animSpeed)
        addSetting(autoWidth)
        addSetting(testPreview)

        testPreview.onChanged = { triggered ->
            if (triggered) {
                testPreview.value = false
                triggerTestPreview()
            }
        }
    }

    override val suffix: String
        get() = "Oscilloscope"

    fun triggerTestPreview() {
        NotificationManager.success("Nocturne Client", "Zinc Precision notification engine active")
        NotificationManager.info("Movement", "Sprint toggled with velocity motion blur")
        NotificationManager.warning("Security", "Anticheat watchdog transaction ping high")
        NotificationManager.error("Network", "Packet connection timeout (404)")
    }
}
