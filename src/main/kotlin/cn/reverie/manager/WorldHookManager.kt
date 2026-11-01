package cn.reverie.manager

import cn.reverie.module.misc.Disabler
import cn.reverie.module.world.Scaffold
import cn.reverie.module.world.TimeChanger
import cn.reverie.module.world.WeatherChanger

object WorldHookManager {
    @JvmStatic fun useHeypixelTeamRules(): Boolean = Disabler.isHeypixelProtocolEnabled()
    @JvmStatic fun isTimeChangerEnabled(): Boolean = TimeChanger.enabled
    @JvmStatic fun effectiveWorldTime(): Long = TimeChanger.getEffectiveTime()
    @JvmStatic fun shouldOverrideWeather(isClientLevel: Boolean): Boolean = isClientLevel && WeatherChanger.enabled
    @JvmStatic fun effectiveRainLevel(): Float = WeatherChanger.getEffectiveRainLevel()
    @JvmStatic fun effectiveThunderLevel(): Float = WeatherChanger.getEffectiveThunderLevel()
    @JvmStatic fun adjustSprintFov(vanilla: Float): Float = Scaffold.onFovModifier(vanilla)
}
