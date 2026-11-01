package cn.reverie.util.network

import com.viaversion.viafabricplus.ViaFabricPlus
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion

object ProtocolUtil {

    fun targetVersion(): ProtocolVersion? {
        return try {
            ViaFabricPlus.api().targetVersion()
        } catch (_: Throwable) {
            null
        }
    }

    fun is1_8OrOlder(): Boolean {
        val version = targetVersion() ?: return false
        return version.olderThanOrEqualTo(ProtocolVersion.v1_8)
    }
}
