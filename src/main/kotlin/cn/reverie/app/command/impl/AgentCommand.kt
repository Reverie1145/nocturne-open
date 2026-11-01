package cn.reverie.app.command.impl

import cn.reverie.app.agent.AgentManager
import cn.reverie.app.command.Command
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.client.multiplayer.ClientSuggestionProvider

class AgentCommand : Command("agent", "AI Agent workspace and task controls", ".agent [ask <task>|stop|new|status]", "ai") {
    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name).executes { AgentManager.open(); 1 }
            .then(literal("ask").then(argument("task", StringArgumentType.greedyString()).executes { ctx ->
                AgentManager.open(showAgent = true)
                AgentManager.engine.submit(StringArgumentType.getString(ctx, "task"))
                ChatUtil.sendInfo("Agent 任务已开始，可在工作区查看工具执行过程")
                1
            }))
            .then(literal("stop").executes { AgentManager.engine.stop(); ChatUtil.sendInfo("正在停止 Agent"); 1 })
            .then(literal("new").executes { AgentManager.engine.newSession(); AgentManager.open(showAgent = true); 1 })
            .then(literal("status").executes {
                val engine = AgentManager.engine
                ChatUtil.sendInfo("Agent: ${engine.state} · round ${engine.round} · ${engine.turnTokens} Tokens")
                1
            })
        val node = dispatcher.register(root)
        aliases.forEach { dispatcher.register(literal(it).executes { AgentManager.open(); 1 }.redirect(node)) }
    }
}
