package cn.reverie.util.rotation


/** Feature-neutral snapshot accessors installed by the module composition root. */
object RotationProfile {
    @Volatile private var instantRotationProvider: () -> Boolean = { false }
    @Volatile private var scaffoldRotationProvider: () -> FloatArray? = { null }

    fun configure(
        instantRotation: () -> Boolean,
        scaffoldRotations: () -> FloatArray?
    ) {
        instantRotationProvider = instantRotation
        scaffoldRotationProvider = scaffoldRotations
    }

    @JvmStatic fun isInstantRotation(): Boolean = instantRotationProvider()
    @JvmStatic fun scaffoldRotations(): FloatArray? = scaffoldRotationProvider()?.clone()
}
