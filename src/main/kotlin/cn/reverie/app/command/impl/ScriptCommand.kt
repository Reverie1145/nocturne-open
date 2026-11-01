package cn.reverie.app.command.impl

import cn.reverie.app.agent.AgentManager
import cn.reverie.app.agent.agentError
import cn.reverie.app.command.Command
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import java.util.concurrent.TimeUnit

class ScriptCommand : Command("script", "Manage local JavaScript scripts", ".script [list|run|stop|reload|check|logs] [id]", "scripts") {
    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name).executes { AgentManager.open(); 1 }
            .then(literal("list").executes {
                ChatUtil.sendInfo(AgentManager.scripts.repository.list().joinToString("\n") { "${it.id}: ${AgentManager.scripts.status(it.id).state}" }.ifBlank { "暂无脚本" })
                1
            })
            .then(literal("stop-all").executes { AgentManager.scripts.stopAll(); ChatUtil.sendInfo("正在停止所有脚本"); 1 })
        for (action in listOf("run", "stop", "reload", "check", "logs")) {
            root.then(literal(action).then(argument("id", StringArgumentType.word()).suggests { _, builder ->
                AgentManager.scripts.repository.list().filter { it.id.startsWith(builder.remaining, true) }.forEach { builder.suggest(it.id) }
                builder.buildFuture()
            }.executes { ctx ->
                val id = StringArgumentType.getString(ctx, "id")
                AgentManager.io.execute {
                    val result = runCatching {
                        val scripts = AgentManager.scripts
                        when (action) {
                            "run" -> scripts.start(id).get(5, TimeUnit.SECONDS).let { "${it.id}: ${it.state} ${it.error}" }
                            "stop" -> scripts.stop(id).get(5, TimeUnit.SECONDS).let { "${it.id}: ${it.state}" }
                            "reload" -> { scripts.stop(id).get(5, TimeUnit.SECONDS); scripts.start(id).get(5, TimeUnit.SECONDS).let { "${it.id}: ${it.state} ${it.error}" } }
                            "check" -> scripts.validate(id) ?: "语法检查通过：$id"
                            else -> scripts.logs(id).takeLast(12).joinToString("\n") { "[${it.level}] ${it.message}" }.ifBlank { "暂无日志" }
                        }
                    }
                    Minecraft.getInstance().execute { result.onSuccess(ChatUtil::sendInfo).onFailure { ChatUtil.sendError(it.agentError()) } }
                }
                1
            }))
        }
        val node = dispatcher.register(root)
        aliases.forEach { dispatcher.register(literal(it).executes { AgentManager.open(); 1 }.redirect(node)) }
    }
}
