package cc.vastsea.zrll.testCommandParser

import cc.vastsea.zrll.commandSystem.CommandRunnerSystem
import cc.vastsea.zrll.commandSystem.CommandSystem
import cc.vastsea.zrll.testCommandParser.commands.TestCommand
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class TestCommandParser : JavaPlugin() {
    private val commandRunnerSystem = CommandRunnerSystem(::i18nGet)

    private val i18nMessages = mapOf(
        "command.syntax.invalid" to "Invalid command format: /{input}",
        "command.syntax.location" to "Problem near `{context}` (character {cursor})\n/{pointer}",
        "command.syntax.available" to "Available usages:",
        "command.syntax.usage" to " - {usage}",
        "command.syntax.check" to "Please check command arguments, or use /{label} help",
        "command.execute.error" to "Error while executing command: {message}"
    )

    private fun i18nGet(key: String, placeholders: Map<String, String>?): String {
        val template = i18nMessages[key] ?: key
        if (placeholders.isNullOrEmpty()) {
            return template
        }
        return placeholders.entries.fold(template) { result, (name, value) ->
            result.replace("{$name}", value)
        }
    }

    override fun onEnable() {
        val commandSystem = CommandSystem()
        commandSystem.register(TestCommand())
        /*
        * You can use the code below to register command as well,
        * but in that way, you don't need to add @CommandHandler annotation to your handler:
        *
        * commandSystem.command("testcommandparser") {
        *     literal("get") {
        *         argument("optional", Int::class, true) {
        *             argument("required", String::class) {
        *                 executes(TestCommand::commandGet)
        *             }
        *         }
        *     }
        * }
        */

        commandSystem.finalize(commandRunnerSystem)
        commandSystem.permissions().forEach {
            Bukkit.getPluginManager().addPermission(it)
        }
        getCommand("testcommandparser")?.let { pluginCommand ->
            pluginCommand.setExecutor(commandRunnerSystem)
            pluginCommand.tabCompleter = commandRunnerSystem
        }
        logger.info("TestCommandParser enabled!")
    }

    override fun onDisable() {
        logger.info("TestCommandParser disabled!")
    }
}
