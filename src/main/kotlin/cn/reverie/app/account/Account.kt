package cn.reverie.app.account

import net.minecraft.client.User
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

data class Account(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var uuid: UUID = generateOfflineUuid(name),
    val type: AccountType = AccountType.OFFLINE,
    var accessToken: String = "0",
    var refreshToken: String = "",
    var xuid: String = "",
    var clientId: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = System.currentTimeMillis(),
    /** OAuth application this account's refresh token belongs to (blank = the default device-code app). */
    var msClientId: String = "",
    var msScope: String = "",
    /** Ban tracking: 0 = clear, -1 = permanently banned, otherwise the epoch millis when the ban ends. */
    var unban: Long = 0L
) {

    fun toUser(): User {
        val xuidOpt = if (xuid.isNotBlank()) Optional.of(xuid) else Optional.empty()
        val clientIdOpt = if (clientId.isNotBlank()) Optional.of(clientId) else Optional.empty()
        val token = if (accessToken.isNotBlank()) accessToken else "0"
        return User(name, uuid, token, xuidOpt, clientIdOpt)
    }

    fun getShortUuid(): String {
        val str = uuid.toString().replace("-", "")
        return if (str.length >= 8) "${str.substring(0, 4)}...${str.substring(str.length - 4)}" else str
    }

    fun getLastUsedFormatted(): String {
        val diffMs = System.currentTimeMillis() - lastUsedAt
        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            diffMs < 0 || seconds < 60 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(lastUsedAt))
        }
    }

    companion object {
        private val NAME_PATTERN = Regex("[A-Za-z0-9_]{3,16}")

        fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name.trim())

        fun generateOfflineUuid(username: String): UUID {
            return UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))
        }

        fun createOffline(name: String): Account {
            val cleanName = name.trim()
            return Account(
                name = cleanName,
                uuid = generateOfflineUuid(cleanName),
                type = AccountType.OFFLINE,
                accessToken = "0"
            )
        }
    }
}
