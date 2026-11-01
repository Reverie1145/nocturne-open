package cn.reverie.app.command.impl

import cn.reverie.app.account.AccountManager
import cn.reverie.app.account.AccountType
import cn.reverie.app.command.Command
import cn.reverie.render.ui.screens.AccountManagerScreen
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider

class AccountCommand : Command(
    "account",
    "Open the account manager, or switch accounts from chat",
    ".account [list|use <name>|offline <name>|random|auto]",
    "acc", "alt"
) {

    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name)
            .executes {
                openScreen()
                1
            }
            .then(literal("list").executes {
                list()
                1
            })
            .then(literal("random").executes {
                random()
                1
            })
            .then(literal("auto").executes {
                toggleAuto()
                1
            })
            .then(
                literal("use").then(
                    argument("name", StringArgumentType.greedyString()).executes { ctx ->
                        use(StringArgumentType.getString(ctx, "name"))
                        1
                    }
                )
            )
            .then(
                literal("offline").then(
                    argument("name", StringArgumentType.greedyString()).executes { ctx ->
                        offline(StringArgumentType.getString(ctx, "name"))
                        1
                    }
                )
            )

        dispatcher.register(root)
        aliases.forEach { alias ->
            dispatcher.register(literal(alias).redirect(dispatcher.root.getChild(name)))
        }
    }

    private fun openScreen() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val parent = mc.gui.screen()
            mc.gui.setScreen(AccountManagerScreen(parent))
        }
    }

    private fun list() {
        if (AccountManager.accounts.isEmpty()) {
            ChatUtil.sendInfo("No accounts saved yet.")
            return
        }

        val activeId = AccountManager.currentAccount?.id
        ChatUtil.sendInfo("Accounts (${AccountManager.accounts.size}):")
        AccountManager.accounts.forEach { account ->
            val marker = if (account.id == activeId) " *" else ""
            ChatUtil.send("  ${account.name} [${account.type.displayName}]$marker")
        }
    }

    private fun use(name: String) {
        val target = AccountManager.accounts.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        if (target == null) {
            ChatUtil.sendError("Account not found: $name")
            return
        }
        AccountManager.login(target) { result ->
            result.onSuccess { ChatUtil.sendSuccess("Logged in as ${it.name}") }
                .onFailure { ChatUtil.sendError("Login failed: ${it.message}") }
        }
    }

    private fun offline(name: String) {
        val clean = name.trim()
        if (!cn.reverie.app.account.Account.isValidName(clean)) {
            ChatUtil.sendError("Use 3-16 letters, digits or underscore")
            return
        }
        val account = AccountManager.loginOffline(clean)
        if (account == null) {
            ChatUtil.sendError("Could not create offline account")
            return
        }
        ChatUtil.sendSuccess("Offline account ready: ${account.name}")
    }

    private fun random() {
        val account = AccountManager.loginRandomOffline()
        if (account == null) {
            ChatUtil.sendError("Could not create random account")
            return
        }
        ChatUtil.sendSuccess("Random alt: ${account.name} [${AccountType.OFFLINE.displayName}]")
    }

    private fun toggleAuto() {
        AccountManager.autoLogin = !AccountManager.autoLogin
        ChatUtil.sendInfo("Auto-login ${if (AccountManager.autoLogin) "enabled" else "disabled"}")
    }
}
