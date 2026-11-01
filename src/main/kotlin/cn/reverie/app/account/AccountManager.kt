package cn.reverie.app.account

import cn.reverie.mixin.client.MinecraftAccessor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import java.io.File
import java.io.FileReader
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import cn.reverie.render.ui.notification.NotificationManager

object AccountManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val randomPrefix = listOf(
        "Nocturne", "Eclipse", "Vortex", "Shadow", "Zenith", "Aether",
        "Frost", "Phantom", "Pulse", "Cyber", "Ghost", "Starlight"
    )
    private val randomSuffix = listOf(
        "Dev", "Knight", "Fox", "Wolf", "Blade", "Sniper",
        "Walker", "Striker", "Storm", "Rider", "Core", "Viper"
    )

    private val ioLock = Any()

    val accounts: MutableList<Account> = CopyOnWriteArrayList()

    var currentAccount: Account? = null
        private set

    var autoLogin: Boolean = true
        set(value) {
            field = value
            save()
        }

    var lastUsedAccountId: String? = null
        private set

    var isInitialized: Boolean = false
        private set

    var isLoggingIn: Boolean = false
        private set

    var isVaultUnreadable: Boolean = false
        private set

    var statusMessage: String = ""
        private set

    var statusTime: Long = 0L
        private set

    private val storageDir: File
        get() {
            val base = try {
                Minecraft.getInstance().gameDirectory
            } catch (_: Throwable) {
                File(".")
            }
            val dir = File(base, "nocturne")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val vaultFile: File get() = File(storageDir, "accounts.dat")

    private val legacyFile: File get() = File(storageDir, "accounts.json")

    fun status(message: String) {
        statusMessage = message
        statusTime = System.currentTimeMillis()
    }

    fun init() {
        if (isInitialized) return
        load()

        val mc = Minecraft.getInstance()
        val initialUser = try { mc.user } catch (_: Throwable) { null }

        if (accounts.isEmpty() && initialUser != null && initialUser.name.isNotBlank()) {
            val defaultAcc = Account(
                name = initialUser.name,
                uuid = initialUser.profileId,
                type = if (initialUser.xuid.isPresent) AccountType.MICROSOFT else AccountType.OFFLINE,
                accessToken = initialUser.accessToken,
                xuid = initialUser.xuid.orElse(""),
                clientId = initialUser.clientId.orElse("")
            )
            accounts.add(defaultAcc)
            currentAccount = defaultAcc
            lastUsedAccountId = defaultAcc.id
            save()
        }

        if (autoLogin && !lastUsedAccountId.isNullOrBlank()) {
            val target = accounts.find { it.id == lastUsedAccountId }
            if (target != null) {
                status("Auto-logging in as ${target.name}...")
                login(target) { result ->
                    result.onSuccess { acc ->
                        NotificationManager.success("Auto Login", "Logged in as ${acc.name}")
                    }.onFailure { err ->
                        NotificationManager.warning("Auto Login", "Failed to auto-login: ${err.message}")
                    }
                }
            }
        } else if (currentAccount == null && accounts.isNotEmpty()) {
            currentAccount = accounts.first()
        }

        isInitialized = true
    }

    fun login(account: Account, onComplete: ((Result<Account>) -> Unit)? = null) {
        val mc = Minecraft.getInstance()
        isLoggingIn = true

        // Microsoft and Cookie accounts: like OpenMyau, try the stored Minecraft token first and only run the
        // refresh chain (MS refresh -> XBL -> XSTS -> Minecraft) when that token no longer works.
        if ((account.type == AccountType.MICROSOFT || account.type == AccountType.COOKIE) && account.refreshToken.isNotBlank()) {
            status("Checking session for ${account.name}...")
            MicrosoftAuth.validateStoredToken(account).whenComplete { valid, _ ->
                if (valid == true) {
                    apply(account, onComplete)
                    return@whenComplete
                }
                status("Refreshing Microsoft session for ${account.name}...")
                MicrosoftAuth.refreshAccount(account).whenComplete { refreshed, error ->
                    mc.execute(Runnable {
                        if (error != null || refreshed == null) {
                            isLoggingIn = false
                            fail(error?.cause ?: error ?: IllegalStateException("Session refresh failed"), account, onComplete)
                        } else {
                            apply(refreshed, onComplete)
                        }
                    })
                }
            }
            return
        }

        apply(account, onComplete)
    }

    private fun apply(account: Account, onComplete: ((Result<Account>) -> Unit)?) {
        val mc = Minecraft.getInstance()
        mc.execute(Runnable {
            try {
                (mc as MinecraftAccessor).nocturneSetUser(account.toUser())
                account.lastUsedAt = System.currentTimeMillis()
                currentAccount = account
                lastUsedAccountId = account.id
                isLoggingIn = false
                save()

                status("Logged in: ${account.name} · ${account.type.displayName}")
                NotificationManager.success("Account Switcher", "Logged in as ${account.name}")
                onComplete?.invoke(Result.success(account))
            } catch (e: Throwable) {
                isLoggingIn = false
                fail(e, account, onComplete)
            }
        })
    }

    private fun fail(error: Throwable, account: Account?, onComplete: ((Result<Account>) -> Unit)?) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Login failed"
        status("Login failed: $message")
        NotificationManager.error("Login Failed", message)

        if (account != null && account.id == lastUsedAccountId && message.contains("credentials expired")) {
            autoLogin = false
        }

        onComplete?.invoke(Result.failure(error))
    }

    fun addAccount(account: Account) {
        val index = accounts.indexOfFirst { it.id == account.id }
            .takeIf { it >= 0 }
            ?: accounts.indexOfFirst {
                it.type == account.type && it.name.equals(account.name, ignoreCase = true)
            }
        if (index >= 0) {
            // Re-adding the same account keeps its ban state (OpenMyau does the same by username).
            if (account.unban == 0L) account.unban = accounts[index].unban
            accounts[index] = account
        } else {
            accounts.add(0, account)
        }
        save()
    }

    fun removeAccount(account: Account) {
        accounts.removeIf { it.id == account.id }
        if (currentAccount?.id == account.id) {
            currentAccount = accounts.firstOrNull()
            lastUsedAccountId = currentAccount?.id
        }
        save()
    }

    fun loginOffline(name: String, autoSave: Boolean = true): Account? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null

        val existing = accounts.find {
            it.name.equals(cleanName, ignoreCase = true) && it.type == AccountType.OFFLINE
        }
        val account = existing ?: Account.createOffline(cleanName)
        if (autoSave && existing == null) {
            accounts.add(0, account)
        }
        login(account)
        return account
    }

    fun randomName(): String {
        val rand = java.util.Random()
        return "${randomPrefix[rand.nextInt(randomPrefix.size)]}${randomSuffix[rand.nextInt(randomSuffix.size)]}_${rand.nextInt(900) + 100}"
    }

    fun loginRandomOffline(): Account? = loginOffline(randomName(), autoSave = true)

    /** Records a ban on every online account with this name (see [AccountBanTracker]). */
    fun markUnban(name: String, unbanAt: Long) {
        var changed = false
        for (acc in accounts) {
            if (acc.type != AccountType.OFFLINE && acc.name == name) {
                acc.unban = unbanAt
                changed = true
            }
        }
        if (changed) save()
    }

    fun findAccount(id: String?): Account? {
        if (id == null) return null
        return accounts.find { it.id == id }
    }

    fun filtered(query: String): List<Account> {
        if (query.isBlank()) return accounts.toList()
        val needle = query.trim().lowercase()
        return accounts.filter {
            it.name.lowercase().contains(needle)
                || it.uuid.toString().lowercase().contains(needle)
                || it.type.displayName.lowercase().contains(needle)
                || it.type.tag.lowercase().contains(needle)
        }
    }

    fun save() {
        synchronized(ioLock) {
            try {
                val root = JsonObject()
                root.addProperty("version", 2)
                root.addProperty("autoLogin", autoLogin)
                if (lastUsedAccountId != null) {
                    root.addProperty("lastUsedAccountId", lastUsedAccountId)
                }

                val array = JsonArray()
                for (acc in accounts) {
                    val obj = JsonObject()
                    obj.addProperty("id", acc.id)
                    obj.addProperty("name", acc.name)
                    obj.addProperty("uuid", acc.uuid.toString())
                    obj.addProperty("type", acc.type.name)
                    obj.addProperty("accessToken", acc.accessToken)
                    obj.addProperty("refreshToken", acc.refreshToken)
                    obj.addProperty("xuid", acc.xuid)
                    obj.addProperty("clientId", acc.clientId)
                    obj.addProperty("addedAt", acc.addedAt)
                    obj.addProperty("lastUsedAt", acc.lastUsedAt)
                    obj.addProperty("msClientId", acc.msClientId)
                    obj.addProperty("msScope", acc.msScope)
                    obj.addProperty("unban", acc.unban)
                    array.add(obj)
                }
                root.add("accounts", array)

                AccountVault.write(vaultFile, gson.toJson(root))

                if (legacyFile.exists()) {
                    legacyFile.delete()
                }
            } catch (e: Throwable) {
                System.err.println("[Nocturne] Failed to save account vault: ${e.message}")
            }
        }
    }

    fun load() {
        synchronized(ioLock) {
            val file = vaultFile

            if (!file.exists()) {
                migrateLegacy()
                return
            }

            val plain = try {
                AccountVault.read(file)
            } catch (e: Throwable) {
                isVaultUnreadable = true
                quarantine(file)
                System.err.println("[Nocturne] Account vault could not be decrypted (machine or user changed?), backed up as .bak: ${e.message}")
                return
            }

            if (plain.isNullOrBlank()) return

            try {
                readInto(plain)
            } catch (e: Throwable) {
                System.err.println("[Nocturne] Failed to parse account vault: ${e.message}")
            }
        }
    }

    private fun migrateLegacy() {
        val legacy = legacyFile
        if (!legacy.exists()) return

        try {
            val plain = FileReader(legacy).use { it.readText() }
            readInto(plain)
            save()
            legacy.delete()
        } catch (e: Throwable) {
            System.err.println("[Nocturne] Failed to migrate legacy accounts.json: ${e.message}")
        }
    }

    private fun readInto(plain: String) {
        val root = JsonParser.parseString(plain).asJsonObject

        if (root.has("autoLogin") && !root.get("autoLogin").isJsonNull) {
            autoLogin = root.get("autoLogin").asBoolean
        }
        if (root.has("lastUsedAccountId") && !root.get("lastUsedAccountId").isJsonNull) {
            lastUsedAccountId = root.get("lastUsedAccountId").asString
        }

        if (!root.has("accounts") || !root.get("accounts").isJsonArray) return

        accounts.clear()
        for (elem in root.getAsJsonArray("accounts")) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject

            val id = obj.get("id")?.asString ?: UUID.randomUUID().toString()
            val name = obj.get("name")?.asString ?: "Player"
            val uuid = try {
                obj.get("uuid")?.asString?.let { UUID.fromString(it) } ?: Account.generateOfflineUuid(name)
            } catch (_: Exception) {
                Account.generateOfflineUuid(name)
            }

            accounts.add(
                Account(
                    id = id,
                    name = name,
                    uuid = uuid,
                    type = AccountType.byName(obj.get("type")?.asString),
                    accessToken = obj.get("accessToken")?.asString ?: "0",
                    refreshToken = obj.get("refreshToken")?.asString ?: "",
                    xuid = obj.get("xuid")?.asString ?: "",
                    clientId = obj.get("clientId")?.asString ?: "",
                    addedAt = obj.get("addedAt")?.asLong ?: System.currentTimeMillis(),
                    lastUsedAt = obj.get("lastUsedAt")?.asLong ?: System.currentTimeMillis(),
                    msClientId = obj.get("msClientId")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    msScope = obj.get("msScope")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    unban = obj.get("unban")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                )
            )
        }

        val used = findAccount(lastUsedAccountId)
        currentAccount = used ?: accounts.firstOrNull()
        lastUsedAccountId = currentAccount?.id
    }

    private fun quarantine(file: File) {
        try {
            val backup = File(file.parentFile, file.name + ".bak")
            java.nio.file.Files.move(
                file.toPath(), backup.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Throwable) {
        }
    }
}
