package cn.reverie.app.config

import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import com.google.gson.JsonParser
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigRoundTripChecks {
    @Test
    fun configNamesCannotEscapeTheProfileDirectory() {
        assertTrue(ConfigManager.isValidConfigName("Standard Config 1"))
        assertFalse(ConfigManager.isValidConfigName("../outside"))
        assertFalse(ConfigManager.isValidConfigName("CON"))
        assertFalse(ConfigManager.isValidConfigName(" padded "))
    }

    @Test
    fun profileRoundTripKeepsTheExistingJsonShapeAndSettingValue() {
        val directory = Files.createTempDirectory("nocturne-config-check").toFile()
        val previousDirectoryOverride = ConfigManager.configDirectoryOverride
        val previousModulesOverride = ConfigManager.modulesOverride
        val previousProfile = ConfigManager.activeConfig
        val module = ConfigTestModule()
        val setting = module.mode
        val originalValue = setting.value
        val profileName = "architecture-round-trip"

        try {
            ConfigManager.configDirectoryOverride = directory
            ConfigManager.modulesOverride = listOf(module)
            assertTrue(ConfigManager.saveConfig(profileName))

            val configFile = directory.resolve("$profileName.json")
            val root = configFile.reader().use { JsonParser.parseReader(it).asJsonObject }
            assertEquals("1.0", root.get("version").asString)
            assertEquals(profileName, root.get("name").asString)
            val savedMode = root.getAsJsonObject("modules")
                .getAsJsonObject("RoundTrip")
                .getAsJsonObject("settings")
                .get("Mode").asString
            assertEquals(originalValue, savedMode)

            setting.value = setting.modes.first { it != originalValue }
            assertTrue(ConfigManager.loadConfig(profileName))
            assertEquals(originalValue, setting.value)
        } finally {
            setting.value = originalValue
            if (ConfigManager.activeConfig != previousProfile) {
                ConfigManager.saveConfig(previousProfile)
            }
            ConfigManager.configDirectoryOverride = previousDirectoryOverride
            ConfigManager.modulesOverride = previousModulesOverride
            directory.deleteRecursively()
        }
    }

    @Test
    fun sharedFullAndSingleModuleConfigsCanBeImportedWithoutChangingTheActiveProfile() {
        val directory = Files.createTempDirectory("nocturne-irc-config-check").toFile()
        val previousDirectoryOverride = ConfigManager.configDirectoryOverride
        val previousModulesOverride = ConfigManager.modulesOverride
        val previousProfile = ConfigManager.activeConfig
        val module = ConfigTestModule()
        val originalValue = module.mode.value
        try {
            ConfigManager.configDirectoryOverride = directory
            ConfigManager.modulesOverride = listOf(module)
            assertTrue(ConfigManager.saveConfig("irc-share-check"))
            val full = ConfigManager.exportSharedConfig()
            val onlyModule = ConfigManager.exportSharedConfig("RoundTrip")
            assertTrue(full != null)
            assertTrue(onlyModule != null)

            module.mode.value = "Second"
            assertTrue(ConfigManager.importSharedConfig(onlyModule))
            assertEquals(originalValue, module.mode.value)
            assertEquals("irc-share-check", ConfigManager.activeConfig)

            module.mode.value = "Second"
            assertTrue(ConfigManager.importSharedConfig(full))
            assertEquals(originalValue, module.mode.value)
            assertEquals("irc-share-check", ConfigManager.activeConfig)
            assertFalse(ConfigManager.importSharedConfig("not json"))
        } finally {
            module.mode.value = originalValue
            if (ConfigManager.activeConfig != previousProfile) ConfigManager.saveConfig(previousProfile)
            ConfigManager.configDirectoryOverride = previousDirectoryOverride
            ConfigManager.modulesOverride = previousModulesOverride
            directory.deleteRecursively()
        }
    }
}

private class ConfigTestModule : Module("RoundTrip", "config serialization check", Category.MISC) {
    val mode = ModeSetting("Mode", "test mode", "First", listOf("First", "Second"))
}
