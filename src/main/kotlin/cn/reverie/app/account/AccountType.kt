package cn.reverie.app.account

enum class AccountType(val displayName: String, val tag: String) {
    OFFLINE("Offline", "OFFLINE"),
    MICROSOFT("Microsoft", "MSA"),
    SESSION("Session", "TOKEN"),
    COOKIE("Cookie", "COOKIE");

    companion object {
        fun byName(raw: String?, fallback: AccountType = OFFLINE): AccountType {
            if (raw.isNullOrBlank()) return fallback
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: fallback
        }
    }
}
