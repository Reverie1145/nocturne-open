package cn.reverie.app.agent

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class AgentPermissionGate {
    private data class Pending(val approval: AgentApproval, val answer: CompletableFuture<Boolean>)
    private val pending = AtomicReference<Pending?>()
    fun current(): AgentApproval? = pending.get()?.approval
    fun answer(id: String, allow: Boolean): Boolean {
        val request = pending.get() ?: return false
        if (request.approval.id != id) return false
        return request.answer.complete(allow)
    }
    fun request(call: AgentToolCall, cancellation: AgentCancellation): Boolean {
        cancellation.check()
        val request = Pending(AgentApproval(UUID.randomUUID().toString(), call.name, agentGson.toJson(call.arguments)), CompletableFuture())
        check(pending.compareAndSet(null, request)) { "已有待确认操作" }
        val hook = cancellation.onCancel { request.answer.cancel(true) }
        return try {
            // Permission expiry is a denial, never inferred consent.
            try { request.answer.get(180, TimeUnit.SECONDS) } catch (_: TimeoutException) { false }
        } finally { hook.close(); pending.compareAndSet(request, null) }
    }
}
