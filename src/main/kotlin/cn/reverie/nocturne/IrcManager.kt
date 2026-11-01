package cn.reverie.nocturne

/**
 * IRC chat transport — UI shell only.
 *
 * The networking layer (socket, TLS pinning, AUTH handshake, image upload) is
 * not part of this open-source build. The click-GUI chat panel is kept so the
 * interface still renders exactly as before, but the client never opens a
 * connection: every outbound call is a no-op and the message history stays
 * empty, so the panel simply shows its offline state.
 */
object IrcManager {

    private const val DEFAULT_CHANNEL = "#nocturne"

    enum class State { IDLE, CONNECTING, CONNECTED, AUTH_FAILED }

    enum class MsgKind { CHAT, NOTICE, JOIN, LEAVE, SYSTEM }

    data class IrcMessage(
        val kind: MsgKind,
        val sender: String,
        val text: String,
        val time: Long,
        val target: String = "",
        val direct: Boolean = false
    )

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var channel: String = DEFAULT_CHANNEL
        private set

    val isConnected: Boolean
        get() = false

    fun recentMessages(): List<IrcMessage> = emptyList()

    fun messagesFor(target: String, direct: Boolean): List<IrcMessage> = emptyList()

    fun channelMembers(): List<String> = emptyList()

    fun privateContacts(): List<String> = emptyList()

    fun uptimeSeconds(): Long = 0L

    fun start(tokenValue: String, username: String) {
        state = State.IDLE
    }

    fun stop() {
        state = State.IDLE
    }

    fun send(message: String, target: String = channel): Boolean = false

    internal fun sendTransport(message: String, target: String): Boolean = false

    internal fun requestMusicPresence() {}

    internal fun publishMusicPresence(payload: String) {}

    fun uploadImage(png: ByteArray, target: String = channel) {}

    fun sendRaw(raw: String) {}
}
