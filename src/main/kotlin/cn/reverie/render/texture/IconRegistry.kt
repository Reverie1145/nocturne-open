package cn.reverie.render.texture

object IconRegistry : AutoCloseable {

    private val icons = HashMap<String, TextureLoader.LoadedImage>()

    fun register(name: String, image: TextureLoader.LoadedImage) {
        icons[name] = image
    }

    fun get(name: String): TextureLoader.LoadedImage? = icons[name]

    fun load(name: String, resourcePath: String): TextureLoader.LoadedImage? {
        val stream = javaClass.getResourceAsStream(resourcePath) ?: return null
        val loaded = stream.use { TextureLoader.loadFromStream(it) } ?: return null
        register(name, loaded)
        return loaded
    }

    override fun close() {
        icons.values.forEach { org.lwjgl.opengl.GL11C.glDeleteTextures(it.textureId) }
        icons.clear()
    }
}
