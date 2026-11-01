package cn.reverie.module.player.bedbreaker

/** Samples once a second and emits only changed identity or bed observations. */
internal class BedTeamChatLog(private val intervalMillis: Long = 1000L) {
    private var lastSampleAt: Long? = null
    private var lastStateKey: String? = null
    private var lastBeds: Map<String, String> = emptyMap()

    fun shouldSample(nowMillis: Long): Boolean {
        val previous = lastSampleAt
        if (previous != null && nowMillis - previous in 0 until intervalMillis) return false
        lastSampleAt = nowMillis
        return true
    }

    fun changes(stateKey: String, stateMessages: List<String>, summary: String, beds: Map<String, String>): List<String> {
        val stateChanged = stateKey != lastStateKey
        val bedsChanged = beds != lastBeds
        if (!stateChanged && !bedsChanged) return emptyList()
        val messages = mutableListOf<String>()
        if (stateChanged) messages += stateMessages
        messages += summary
        for ((key, message) in beds) {
            if (stateChanged || lastBeds[key] != message) messages += message
        }
        lastStateKey = stateKey
        lastBeds = beds.toMap()
        return messages
    }

    fun reset() {
        lastSampleAt = null
        lastStateKey = null
        lastBeds = emptyMap()
    }
}
