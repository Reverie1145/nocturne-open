package cn.reverie.nocturne

import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 设备指纹：优先用 Windows MachineGuid，取不到时回退到物理网卡 + 硬件信息组合。
 * 结果只做 SHA-256 后截取，不含任何可反推的原始标识。
 */
object HWID {

    private const val SALT = "nocturne-hwid-v1"

    @Volatile
    private var cached: String? = null

    fun value(): String {
        cached?.let { return it }
        val computed = try {
            compute()
        } catch (t: Throwable) {
            ""
        }
        cached = computed
        return computed
    }

    private fun compute(): String {
        val guid = readMachineGuid()
        val material = if (!guid.isNullOrBlank()) {
            "mg|$guid"
        } else {
            "fb|" + physicalMacs().joinToString(",") + "|" +
                System.getProperty("os.arch", "?") + "|" +
                Runtime.getRuntime().availableProcessors() + "|" +
                System.getProperty("os.version", "?")
        }
        return sha256Hex("$SALT|$material").substring(0, 32)
    }

    private fun readMachineGuid(): String? {
        return try {
            val pb = ProcessBuilder(
                "reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"
            )
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return null
            }
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                .find(out)?.groupValues?.get(1)?.lowercase()
        } catch (t: Throwable) {
            null
        }
    }

    private val VIRTUAL_HINTS = listOf(
        "virtual", "vmware", "vbox", "hyper-v", "vethernet", "tap", "tun",
        "loopback", "bluetooth", "vpn", "docker", "wintun", "wireguard"
    )

    private fun physicalMacs(): List<String> {
        val out = mutableListOf<String>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                val label = (ni.name + " " + (ni.displayName ?: "")).lowercase()
                if (VIRTUAL_HINTS.any { label.contains(it) }) continue
                val mac = ni.hardwareAddress ?: continue
                if (mac.size < 6 || mac.all { it.toInt() == 0 }) continue
                out.add(mac.joinToString("") { "%02x".format(it) })
            }
        } catch (t: Throwable) {
        }
        return out.sorted()
    }

    private fun sha256Hex(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
